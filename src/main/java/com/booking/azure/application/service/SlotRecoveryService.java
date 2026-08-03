package com.booking.azure.application.service;

import com.booking.azure.domain.model.Booking;
import com.booking.azure.domain.model.OrphanedReservation;
import com.booking.azure.domain.model.SlotStatus;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.TimeWindow;
import com.booking.azure.application.port.in.AppointmentManagement;
import com.booking.azure.domain.port.out.BookingRepository;
import com.booking.azure.domain.port.out.DomainEventPublisher;
import com.booking.azure.dto.BookingAppointmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Recuperación de reservas {@code PENDING} huérfanas.
 *
 * <h2>Por qué este servicio tiene que existir</h2>
 *
 * Como la compensación distingue «Graph rechazó» de «Graph no contestó», cada
 * timeout deja una reserva en {@code PENDING}. Eso es correcto —evita la doble
 * reserva—, pero sin este servicio el hueco quedaría bloqueado <b>para
 * siempre</b>.
 *
 * <h2>La única regla que importa</h2>
 *
 * <b>Primero preguntar a Graph, después decidir.</b> Un job que libere las filas
 * caducadas por horario provoca exactamente el fallo que pretende arreglar:
 *
 * <pre>
 *   t=0     A reserva, expires_at = t+90. Se envía el POST a Graph.
 *   t=0,1   Graph está saturado y tarda 120 s.
 *   t=90    Un job ciego libera la fila «caducada». Hueco libre.
 *   t=91    B ocupa el hueco, POST → 201
 *   t=120   El POST de A llega a Graph de todas formas → 201
 *           Dos citas solapadas. En silencio.
 * </pre>
 *
 * <p><b>Que una fila caduque no aborta una llamada HTTP en vuelo.</b>
 * {@code expires_at} no demuestra que la cita no exista: solo marca la fila como
 * <i>digna de comprobación</i>, nunca como <i>liberable</i>.
 *
 * <p>Por eso este servicio consulta {@code GET /calendarView} para cada fila
 * huérfana y solo entonces decide:
 *
 * <ul>
 *   <li>cita encontrada → {@code CONFIRMED}. <b>Restaurar, no liberar</b>: la
 *       escritura sí ocurrió.</li>
 *   <li>cita ausente → {@code RELEASED}. El hueco vuelve a ser reservable.</li>
 *   <li>Graph inalcanzable → <b>no tocar nada</b> y reintentar en la siguiente
 *       vuelta. Sin respuesta no hay base para decidir, y quedarse quieto es el
 *       lado seguro.</li>
 * </ul>
 *
 * <h2>El cotejo es por solape, no por igualdad</h2>
 *
 * Bookings puede añadir márgenes de preparación antes y después, y Graph
 * devuelve las horas en zonas distintas según el endpoint. Una comparación
 * estricta fallaría al reconocer la cita y liberaría el hueco por error — que es
 * el fallo más caro de los dos.
 *
 * <p>Véase {@code docs/PLAN-COLISION-RESERVAS.md} §2.5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotRecoveryService {

    /**
     * Padding around the slot window when querying the calendar view.
     *
     * Microsoft Bookings can add buffer time before and after an appointment, so
     * the returned times do not match the requested ones exactly.
     */
    private static final Duration SEARCH_PADDING = Duration.ofHours(1);

    private final BookingRepository bookingRepository;
    private final AppointmentManagement appointmentManagement;
    private final DomainEventPublisher eventPublisher;

    /**
     * Periodic entry point. The actual work lives in {@link #recoverOrphaned()}
     * so that tests can call it directly.
     */
    @Scheduled(cron = "${buchung.slot-reservierung.wiederherstellung-cron:0 */5 * * * *}")
    public void scheduledRun() {
        recoverOrphaned();
    }

    /**
     * Checks every expired {@code PENDING} reservation against Graph and either
     * confirms or releases it.
     *
     * @return number of reservations processed
     */
    public int recoverOrphaned() {
        List<OrphanedReservation> orphaned = bookingRepository.findOrphaned(Instant.now());
        if (orphaned.isEmpty()) {
            return 0;
        }

        log.info("{} orphaned slot reservation(s) will be checked against Graph", orphaned.size());

        // Group by business: one calendar query per agency instead of per row.
        // An appointment with several staff members produces several rows for the
        // same period, which would otherwise trigger the same query repeatedly.
        Map<BusinessId, List<OrphanedReservation>> byBusiness = orphaned.stream()
                .collect(Collectors.groupingBy(OrphanedReservation::businessId));

        int processed = 0;
        for (Map.Entry<BusinessId, List<OrphanedReservation>> entry : byBusiness.entrySet()) {
            processed += checkBusiness(entry.getKey(), entry.getValue());
        }
        return processed;
    }

    private int checkBusiness(BusinessId businessId, List<OrphanedReservation> orphaned) {
        TimeWindow searchWindow = searchWindowCovering(orphaned);

        List<BookingAppointmentDto> appointments;
        try {
            appointments = appointmentManagement.getCalendarView(
                    businessId.value(), searchWindow.start().toString(), searchWindow.end().toString());
        } catch (RuntimeException ex) {
            // Without an answer from Graph there is no basis for a decision.
            // Touch nothing and retry on the next run – the rows stay PENDING and
            // the slot stays blocked, which is the safe side.
            log.warn("Calendar view for business {} unavailable, {} reservation(s) left untouched: {}",
                    businessId, orphaned.size(), ex.getMessage());
            return 0;
        }

        int processed = 0;
        for (OrphanedReservation row : orphaned) {
            // Load the whole aggregate: the decision applies to every slot the
            // booking holds, not to the one row that happened to be scanned.
            // A booking with two staff members must not end up half confirmed.
            Optional<Booking> loaded = bookingRepository.findById(row.bookingId());
            if (loaded.isEmpty()) {
                log.warn("Orphaned reservation {} refers to unknown booking {}, skipped",
                        row.id(), row.bookingId());
                continue;
            }
            Booking booking = loaded.get();

            // A concurrent request may have settled it between the scan and now.
            if (booking.status() != SlotStatus.PENDING) {
                log.debug("Booking {} already {}, nothing to recover", booking.id(), booking.status());
                continue;
            }

            Optional<BookingAppointmentDto> match = findMatchingAppointment(row, appointments);

            if (match.isPresent()) {
                // The write did happen. Restore, do NOT release.
                booking.confirm(AppointmentId.of(match.get().getId()));
                bookingRepository.save(booking);
                eventPublisher.publishAll(booking.pullEvents());
                log.info("Orphaned booking {} restored: appointment {} exists in Graph",
                        booking.id(), match.get().getId());
            } else {
                booking.release();
                bookingRepository.save(booking);
                eventPublisher.publishAll(booking.pullEvents());
                log.info("Orphaned booking {} released: no matching appointment in Graph "
                        + "(staff member {}, {})", booking.id(), row.staffMemberId(), row.window());
            }
            processed++;
        }
        return processed;
    }

    /** The smallest window covering every orphaned row, widened by the search padding. */
    private TimeWindow searchWindowCovering(List<OrphanedReservation> orphaned) {
        Instant from = orphaned.stream().map(row -> row.window().start())
                .min(Instant::compareTo).orElseThrow();
        Instant to = orphaned.stream().map(row -> row.window().end())
                .max(Instant::compareTo).orElseThrow();
        return TimeWindow.of(from, to).paddedBy(SEARCH_PADDING);
    }

    /**
     * Finds an appointment matching the orphaned reservation.
     *
     * Criteria: the same staff member and an overlapping period.
     *
     * <p>Overlap rather than exact equality on purpose: Bookings can add buffer
     * time, and Graph returns times in different zones depending on the
     * endpoint. Too strict a comparison would miss the appointment and wrongly
     * release the slot — the more expensive mistake.
     */
    private Optional<BookingAppointmentDto> findMatchingAppointment(
            OrphanedReservation row, List<BookingAppointmentDto> appointments) {

        return appointments.stream()
                .filter(appointment -> appointment.getStaffMemberIds() != null
                        && appointment.getStaffMemberIds().contains(row.staffMemberId().value()))
                .filter(appointment -> overlaps(row, appointment))
                .findFirst();
    }

    private boolean overlaps(OrphanedReservation row, BookingAppointmentDto appointment) {
        try {
            TimeWindow appointmentWindow = TimeWindow.of(
                    TimeZoneConverter.toInstant(appointment.getStartDateTime()),
                    TimeZoneConverter.toInstant(appointment.getEndDateTime()));
            return row.window().overlaps(appointmentWindow);
        } catch (IllegalArgumentException ex) {
            log.warn("Appointment {} has unreadable times and is skipped during matching: {}",
                    appointment.getId(), ex.getMessage());
            return false;
        }
    }
}
