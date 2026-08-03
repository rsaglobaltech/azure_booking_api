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
 * Infrastructure adapter: implements {@link BookingRepository} on Oracle.
 *
 * <h2>Division of responsibility</h2>
 *
 * The aggregate decides <i>what</i> state a booking is in; this class decides
 * <i>how</i> that state reaches the database, and enforces the one invariant the
 * aggregate cannot see: that two different bookings must not overlap.
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
     * CORE COLLISION ALGORITHM (Oracle compatible)
     *
     * Oracle does not support PostgreSQL's {@code EXCLUDE USING gist} constraint
     * (as originally described in docs/PLAN-COLISION-RESERVAS.md), so double
     * bookings are prevented programmatically with pessimistic locking:
     *
     * <ol>
     *   <li><b>Lock</b> — acquire a {@code PESSIMISTIC_WRITE} lock on the staff
     *       member's row. This serialises all incoming booking requests for that
     *       staff member, so only one transaction at a time can proceed.</li>
     *   <li><b>Check</b> — query {@code countOverlappingReservations} for any
     *       existing {@code PENDING} or {@code CONFIRMED} reservation overlapping
     *       the requested window for that staff member.</li>
     *   <li><b>Act</b> — if there is any overlap, abort with
     *       {@link SlotConflictException} (HTTP 409). Otherwise insert the rows
     *       and commit, releasing the lock.</li>
     * </ol>
     *
     * {@code saveAllAndFlush} is essential: it forces the write to the database
     * before the lock is released.
     *
     * <p>This is the invariant the aggregate cannot enforce on its own — a
     * booking knows nothing about bookings it has never loaded.
     */
    private void store(Booking booking, SlotRequest request, Instant now) {
        TimeWindow window = request.window();

        // 1. Lock the staff members and check for overlaps BEFORE inserting
        for (StaffMemberId staffMemberId : request.staffMemberIds()) {
            // Pessimistic lock on the staff member row serialises requests for that member
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

        // 2. Insert, then hand the generated ids back to the aggregate
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
