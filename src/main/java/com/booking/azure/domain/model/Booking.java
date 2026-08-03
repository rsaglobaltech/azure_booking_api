package com.booking.azure.domain.model;

import com.booking.azure.domain.event.BookingConfirmed;
import com.booking.azure.domain.event.BookingReleased;
import com.booking.azure.domain.event.SlotsReserved;
import com.booking.azure.domain.exception.IllegalBookingStateException;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.CustomerContact;
import com.booking.azure.domain.model.vo.BookingId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A held booking: aggregate root over the slot reservations it owns.
 *
 * <h2>The invariant this aggregate exists to protect</h2>
 *
 * <b>All or nothing.</b> A booking assigned to three staff members holds three
 * slots or none. Every transition therefore applies to the whole set: there is
 * no way to confirm two reservations and release the third, because callers
 * cannot reach the reservations individually.
 *
 * <h2>Lifecycle</h2>
 *
 * <pre>
 *   request()  → PENDING     slots held, Graph knows nothing yet
 *   confirm()  → CONFIRMED   Graph accepted the write, appointment id attached
 *   release()  → RELEASED    compensation, cancellation, or reschedule
 * </pre>
 *
 * {@code PENDING} and {@code CONFIRMED} both block the slot; only
 * {@code RELEASED} frees it.
 *
 * <h2>What this aggregate does not own</h2>
 *
 * The rule that two <i>different</i> bookings must not overlap crosses
 * aggregate boundaries and cannot be checked from in-memory state — a booking
 * knows nothing about bookings it has never loaded. That invariant is enforced
 * at the database, by a pessimistic lock on the staff member row plus an
 * overlap check, inside the transaction that writes the reservations. See
 * {@code BookingJpaAdapter}.
 */
public class Booking extends AggregateRoot {

    private final BookingId id;
    private final BusinessId businessId;
    private final ServiceId serviceId;
    private final TimeWindow window;
    private final List<SlotReservation> reservations;

    private AppointmentId appointmentId;

    /**
     * Who the booking is for, when known.
     *
     * <p>Deliberately not persisted: Microsoft Bookings owns the customer record,
     * and keeping a second copy of that personal data locally would mean
     * maintaining and protecting it for no gain. The consequence is that a
     * booking rebuilt from storage has no customer — see {@code BookingConfirmed}.
     */
    private CustomerContact customer;

    private Booking(BookingId id,
                    BusinessId businessId,
                    ServiceId serviceId,
                    TimeWindow window,
                    List<SlotReservation> reservations,
                    AppointmentId appointmentId) {
        if (id == null) {
            throw new IllegalArgumentException("bookingId is required");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("businessId is required");
        }
        if (window == null) {
            throw new IllegalArgumentException("window is required");
        }
        if (reservations == null || reservations.isEmpty()) {
            throw new IllegalArgumentException("a booking holds at least one reservation");
        }
        this.id = id;
        this.businessId = businessId;
        this.serviceId = serviceId;
        this.window = window;
        this.reservations = List.copyOf(reservations);
        this.appointmentId = appointmentId;
    }

    /**
     * Creates a booking holding one {@code PENDING} reservation per staff member.
     *
     * @param request   business, service, staff and window
     * @param expiresAt when the holds become worth checking against Graph
     */
    public static Booking request(SlotRequest request, Instant expiresAt) {
        List<SlotReservation> reservations = request.staffMemberIds().stream()
                .map(staffMemberId ->
                        SlotReservation.pending(staffMemberId, request.window(), expiresAt))
                .toList();

        Booking booking = new Booking(
                BookingId.generate(),
                request.businessId(),
                request.serviceId(),
                request.window(),
                reservations,
                null);

        booking.registerEvent(new SlotsReserved(
                booking.id, booking.businessId, booking.serviceId,
                booking.window, booking.staffMemberIds()));

        return booking;
    }

    /**
     * Attaches the customer the booking is for.
     *
     * Separate from {@link #request} because the customer is not part of what
     * makes a slot reservation valid — a booking holds its window whether or not
     * anyone has said who it is for.
     */
    public Booking forCustomer(CustomerContact customer) {
        this.customer = customer;
        return this;
    }

    public Optional<CustomerContact> customer() {
        return Optional.ofNullable(customer);
    }

    /**
     * Creates the replacement booking for an appointment being moved.
     *
     * The reservations stay {@code PENDING} even though the appointment id is
     * already known: Graph has not yet accepted the move, and a booking that
     * claimed {@code CONFIRMED} before the write landed would be invisible to
     * the recovery job — which only inspects {@code PENDING} rows — leaving the
     * slot blocked forever if the process died mid-reschedule.
     */
    public static Booking rescheduleOf(SlotRequest request,
                                       Instant expiresAt,
                                       AppointmentId appointmentId) {
        if (appointmentId == null) {
            throw new IllegalArgumentException("appointmentId is required when rescheduling");
        }
        Booking booking = request(request, expiresAt);
        return new Booking(booking.id, booking.businessId, booking.serviceId,
                booking.window, booking.reservations, appointmentId);
    }

    /** Rebuilds a booking from storage. */
    public static Booking rehydrate(BookingId id,
                                    BusinessId businessId,
                                    ServiceId serviceId,
                                    TimeWindow window,
                                    List<SlotReservation> reservations,
                                    AppointmentId appointmentId) {
        return new Booking(id, businessId, serviceId, window, reservations, appointmentId);
    }

    public BookingId id() {
        return id;
    }

    public BusinessId businessId() {
        return businessId;
    }

    public ServiceId serviceId() {
        return serviceId;
    }

    public TimeWindow window() {
        return window;
    }

    public AppointmentId appointmentId() {
        return appointmentId;
    }

    /** Read-only view; transitions go through this root, never through a member. */
    public List<SlotReservation> reservations() {
        return reservations;
    }

    public List<StaffMemberId> staffMemberIds() {
        return reservations.stream().map(SlotReservation::staffMemberId).toList();
    }

    /**
     * The booking's state, which by construction is the state of every one of
     * its reservations.
     */
    public SlotStatus status() {
        return reservations.get(0).status();
    }

    public boolean isBlocking() {
        return status() == SlotStatus.PENDING || status() == SlotStatus.CONFIRMED;
    }

    /**
     * Records that Graph accepted the write and links the appointment.
     *
     * <p>Idempotent for the same appointment: the recovery job may confirm a
     * booking that a concurrent request has already confirmed.
     *
     * @throws IllegalBookingStateException if the slots were already released,
     *         or if a different appointment is already attached
     */
    public void confirm(AppointmentId appointmentId) {
        if (appointmentId == null) {
            throw new IllegalArgumentException("appointmentId is required to confirm");
        }
        if (status() == SlotStatus.RELEASED) {
            throw new IllegalBookingStateException(id, SlotStatus.RELEASED, "be confirmed");
        }
        if (this.appointmentId != null && !this.appointmentId.equals(appointmentId)) {
            throw new IllegalBookingStateException(id, status(),
                    "be confirmed as %s, already bound to %s"
                            .formatted(appointmentId, this.appointmentId));
        }

        this.appointmentId = appointmentId;
        reservations.forEach(reservation -> reservation.confirm(appointmentId));

        registerEvent(new BookingConfirmed(id, appointmentId, businessId, serviceId,
                window, staffMemberIds(), customer));
    }

    /**
     * Frees every slot this booking holds.
     *
     * Idempotent: cancelling an already-cancelled booking is not an error, and
     * compensation paths can run more than once.
     */
    public void release() {
        if (status() == SlotStatus.RELEASED) {
            return;
        }
        reservations.forEach(SlotReservation::release);

        registerEvent(new BookingReleased(id, businessId, window, staffMemberIds(), appointmentId));
    }

    /**
     * Whether any of this booking's held slots overlaps the given window.
     *
     * Released reservations do not count — they no longer hold anything.
     */
    public boolean overlaps(TimeWindow other) {
        return reservations.stream()
                .filter(SlotReservation::isBlocking)
                .anyMatch(reservation -> reservation.overlaps(other));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Booking that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Booking %s %s %s [%s]".formatted(id, businessId, window, status());
    }
}
