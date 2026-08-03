package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.domain.exception.SlotConflictException;
import com.booking.azure.domain.model.Booking;
import com.booking.azure.domain.model.OrphanedReservation;
import com.booking.azure.domain.model.SlotRequest;
import com.booking.azure.domain.model.SlotReservation;
import com.booking.azure.domain.model.SlotStatus;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BookingId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;
import com.booking.azure.domain.port.out.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adaptador de infraestructura: implementa {@link BookingRepository} sobre Oracle.
 *
 * <h2>Reparto de responsabilidades</h2>
 *
 * El agregado decide <i>en qué</i> estado está una reserva; esta clase decide
 * <i>cómo</i> llega ese estado a la base de datos, y hace cumplir la única
 * invariante que el agregado no puede ver: que dos reservas distintas no se
 * solapen.
 *
 * <h2>Por qué la exclusión mutua vive aquí y no en Microsoft Graph</h2>
 *
 * Graph no puede ser la autoridad. Sobre {@code bookingAppointment} no ofrece
 * <b>bloqueos</b>, ni <b>transacciones</b>, ni <b>escrituras condicionales</b>:
 * no existe un «crea la cita si el hueco está libre». Y el endpoint que usamos
 * es el administrativo, que permite el sobrecupo <b>a propósito</b>, para que el
 * personal pueda encajar citas aunque la agenda esté formalmente llena.
 *
 * <p>Resultado: dos peticiones simultáneas para el mismo médico y la misma hora
 * reciben <b>ambas</b> un {@code 201 Created}, y nadie se entera hasta que dos
 * clientes se presentan a la vez.
 *
 * <p>Por eso este sistema mantiene su propia tabla {@code slot_reservation} y
 * decide aquí, <b>antes</b> de que Graph llegue a enterarse.
 */
@Slf4j
@Component
public class BookingJpaAdapter implements BookingRepository {

    private static final List<SlotStatus> BLOCKING =
            List.of(SlotStatus.PENDING, SlotStatus.CONFIRMED);

    private final SlotReservationRepository repository;
    private final SpringDataStaffRepository staffRepository;
    private final BookingMapper mapper;
    private final Duration reservationTtl;

    public BookingJpaAdapter(
            SlotReservationRepository repository,
            SpringDataStaffRepository staffRepository,
            BookingMapper mapper,
            @Value("${buchung.slot-reservierung.ttl:PT90S}") Duration reservationTtl) {
        this.repository = repository;
        this.staffRepository = staffRepository;
        this.mapper = mapper;
        this.reservationTtl = reservationTtl;
    }

    @Override
    @Transactional
    public Booking reserve(SlotRequest request) {
        Instant now = Instant.now();
        Booking booking = Booking.request(request, now.plus(reservationTtl));

        store(booking, request, now);
        return booking;
    }

    @Override
    @Transactional
    public void save(Booking booking) {
        Instant now = Instant.now();

        Map<Long, SlotReservationEntity> rowsById =
                repository.findByBookingId(booking.id().value()).stream()
                        .collect(Collectors.toMap(SlotReservationEntity::getId, Function.identity()));

        List<SlotReservationEntity> touched = booking.reservations().stream()
                .map(reservation -> {
                    SlotReservationEntity row = rowsById.get(reservation.id());
                    if (row == null) {
                        // The aggregate carries a reservation the database does not
                        // know. Writing it now would create a hold that never passed
                        // the overlap check, so refuse instead.
                        throw new IllegalStateException(
                                "reservation %s of booking %s is missing from storage"
                                        .formatted(reservation.id(), booking.id()));
                    }
                    mapper.applyState(booking, reservation, row, now);
                    return row;
                })
                .toList();

        repository.saveAll(touched);
        log.debug("Booking {} saved as {}", booking.id(), booking.status());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findById(BookingId bookingId) {
        List<SlotReservationEntity> rows = repository.findByBookingId(bookingId.value());
        return rows.isEmpty() ? Optional.empty() : Optional.of(mapper.toDomain(rows));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findBlockingByAppointmentId(AppointmentId appointmentId) {
        List<SlotReservationEntity> rows =
                repository.findByGraphAppointmentIdAndStateIn(appointmentId.value(), BLOCKING);
        return rows.isEmpty() ? Optional.empty() : Optional.of(mapper.toDomain(rows));
    }

    /**
     * Releasing the old booking and taking the new window in <b>one</b>
     * transaction.
     *
     * Separate transactions would be wrong: in between, another request could
     * claim the old slot, and if taking the new window then failed the old one
     * would be irrecoverably lost.
     */
    @Override
    @Transactional
    public Booking reschedule(AppointmentId appointmentId, SlotRequest newWindow) {
        Instant now = Instant.now();

        List<SlotReservationEntity> oldRows =
                repository.findByGraphAppointmentIdAndStateIn(appointmentId.value(), BLOCKING);

        if (!oldRows.isEmpty()) {
            Booking old = mapper.toDomain(oldRows);
            old.release();
            Map<Long, SlotReservation> byId = old.reservations().stream()
                    .collect(Collectors.toMap(SlotReservation::id, Function.identity()));
            oldRows.forEach(row -> mapper.applyState(old, byId.get(row.getId()), row, now));
            repository.saveAll(oldRows);
        }

        // Without this flush the overlap check below would not yet see the
        // release. Moving an appointment onto a window overlapping its own
        // (e.g. 10:00–11:00 → 10:30–11:30) would otherwise collide with itself.
        repository.flush();

        Booking booking = Booking.rescheduleOf(newWindow, now.plus(reservationTtl), appointmentId);

        store(booking, newWindow, now);
        return booking;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrphanedReservation> findOrphaned(Instant before) {
        return repository.findByStateAndExpiresAtBefore(SlotStatus.PENDING, before).stream()
                .map(row -> new OrphanedReservation(
                        row.getId(),
                        BookingId.of(row.getBookingId()),
                        BusinessId.of(row.getBusinessId()),
                        ServiceId.of(row.getServiceId()),
                        StaffMemberId.of(row.getStaffMemberId()),
                        TimeWindow.of(row.getStartUtc(), row.getEndUtc())))
                .toList();
    }

    // ───────────────────────────────── helpers ─────────────────────────────────

    /**
     * ALGORITMO DE COLISIÓN — el corazón del sistema.
     *
     * <h2>De dónde viene</h2>
     *
     * El diseño original (véase {@code docs/PLAN-COLISION-RESERVAS.md}) se
     * apoyaba en PostgreSQL, donde la propia base de datos rechaza las filas
     * solapadas:
     *
     * <pre>
     * CONSTRAINT ex_slot_overlap EXCLUDE USING gist (
     *     staff_member_id WITH =,
     *     tsrange(start_utc, end_utc) WITH &amp;&amp;
     * ) WHERE (state IN ('PENDING', 'CONFIRMED'))
     * </pre>
     *
     * Oracle no tiene {@code EXCLUDE USING gist} ni equivalente, así que la
     * exclusión se impone por código, con bloqueo pesimista.
     *
     * <h2>Los tres pasos, y por qué ese orden</h2>
     *
     * <ol>
     *   <li><b>Bloquear</b> — {@code PESSIMISTIC_WRITE} sobre la fila del
     *       empleado ({@code SELECT ... FOR UPDATE}). Esto <b>serializa</b> todas
     *       las peticiones para ese mismo empleado: solo una transacción avanza
     *       cada vez.</li>
     *   <li><b>Comprobar</b> — {@code countOverlappingReservations} busca
     *       reservas bloqueantes ({@code PENDING} o {@code CONFIRMED}) que se
     *       solapen con la ventana pedida.</li>
     *   <li><b>Actuar</b> — si hay solape, se aborta con
     *       {@link SlotConflictException} (HTTP 409). Si no, se insertan las
     *       filas y se confirma la transacción, liberando el bloqueo.</li>
     * </ol>
     *
     * <p><b>Bloquear antes de comprobar es todo el asunto.</b> Sin el bloqueo,
     * dos transacciones podrían leer las dos «no hay conflicto» y luego insertar
     * las dos:
     *
     * <pre>
     *   t=0   A comprueba solapes → 0
     *   t=1   B comprueba solapes → 0   (A aún no ha insertado)
     *   t=2   A inserta. t=3 B inserta. Doble reserva.
     * </pre>
     *
     * <p>{@code saveAllAndFlush} y no {@code saveAll}: la escritura tiene que
     * llegar a la base de datos <b>antes</b> de que el commit suelte el bloqueo.
     *
     * <h2>El bloqueo va sobre el empleado, no sobre la tabla</h2>
     *
     * Así las reservas de empleados distintos siguen corriendo en paralelo. Solo
     * se hace cola cuando compiten por la misma persona, que es exactamente
     * cuando debe haberla.
     *
     * <h2>Lo que esta decisión cuesta</h2>
     *
     * <b>La regla ya no vive en el esquema.</b> Solo se aplica a las escrituras
     * que pasan por este adaptador. Un script que inserte directamente en
     * {@code slot_reservation} genera dobles reservas sin que la base de datos
     * proteste. Con la restricción {@code EXCLUDE} de PostgreSQL eso era
     * imposible.
     *
     * <h2>Por qué no está en el agregado</h2>
     *
     * Esta invariante cruza fronteras de agregado y no es comprobable en
     * memoria: un {@code Booking} no sabe nada de reservas que nunca ha cargado.
     * Por eso se queda aquí, y aquí se documenta.
     */
    private void store(Booking booking, SlotRequest request, Instant now) {
        TimeWindow window = request.window();

        // 1. Bloquear y comprobar ANTES de insertar, empleado por empleado.
        //    Si la reserva abarca varios empleados y uno solo está ocupado,
        //    falla la petición entera: o se reservan todos los huecos o ninguno.
        for (StaffMemberId staffMemberId : request.staffMemberIds()) {
            // Bloqueo pesimista sobre la fila del empleado: serializa las
            // peticiones que compiten por esa misma persona.
            staffRepository.lockByMsStaffMemberId(staffMemberId.value());

            int overlaps = repository.countOverlappingReservations(
                    request.businessId().value(),
                    staffMemberId.value(),
                    window.start(),
                    window.end());

            if (overlaps > 0) {
                log.info("Slot already taken: business={}, staffMember={}, {}",
                        request.businessId(), staffMemberId, window);
                throw new SlotConflictException(
                        "The window %s is already taken for at least one of the staff members %s."
                                .formatted(window, request.staffMemberIds()));
            }
        }

        // 2. Insertar y devolver al agregado los identificadores generados
        List<SlotReservationEntity> rows = booking.reservations().stream()
                .map(reservation -> mapper.toNewEntity(booking, reservation, now))
                .toList();

        List<SlotReservationEntity> saved;
        try {
            saved = repository.saveAllAndFlush(rows);
        } catch (DataIntegrityViolationException ex) {
            log.info("DataIntegrityViolationException while saving slot: {}", ex.getMessage());
            throw new SlotConflictException("Database error while reserving the slot", ex);
        }

        List<SlotReservation> reservations = booking.reservations();
        for (int i = 0; i < reservations.size(); i++) {
            reservations.get(i).assignId(saved.get(i).getId());
        }
    }
}
