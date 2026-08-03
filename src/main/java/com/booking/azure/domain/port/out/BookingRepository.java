package com.booking.azure.domain.port.out;

import com.booking.azure.domain.exception.SlotConflictException;
import com.booking.azure.domain.model.Booking;
import com.booking.azure.domain.model.OrphanedReservation;
import com.booking.azure.domain.model.SlotRequest;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BookingId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for storing and retrieving the {@link Booking} aggregate.
 *
 * Domain layer: defines the contract; the implementation
 * ({@code BookingJpaAdapter}) lives in the infrastructure layer.
 *
 * <h2>Why this port owns mutual exclusion</h2>
 *
 * Microsoft Graph cannot be the authority: it offers neither locks nor
 * transactions nor conditional writes on {@code bookingAppointment}, and the
 * endpoint in use is the administrative one, which permits overbooking on
 * purpose. The rule that two bookings must not overlap therefore lives here,
 * enforced inside the transaction that writes the reservations.
 *
 * <h2>Intended call order</h2>
 *
 * <pre>
 *   1. reserve(...)              → own transaction, committed immediately
 *   2. POST to Microsoft Graph   → outside any transaction
 *   3a. booking.confirm(id); save(booking)   → on success
 *   3b. booking.release();      save(booking)   → on failure (compensation)
 * </pre>
 *
 * <p><b>Step 2 must not run inside an open transaction.</b> Holding one open
 * across a network call exhausts the connection pool under load.
 */
public interface BookingRepository {

    /**
     * Creates and persists a booking holding the window for every requested
     * staff member — all or nothing.
     *
     * @param request window in UTC, business, service and staff members
     * @return the persisted booking, its reservations carrying their assigned ids
     * @throws SlotConflictException if the window overlaps a blocking
     *         reservation for at least one staff member
     */
    Booking reserve(SlotRequest request);

    /**
     * Writes the aggregate's current state back to storage.
     *
     * Used after {@link Booking#confirm} and {@link Booking#release}. The
     * transitions themselves are the aggregate's business, not this port's.
     */
    void save(Booking booking);

    Optional<Booking> findById(BookingId bookingId);

    /**
     * Loads the booking linked to a Graph appointment, if it still holds slots.
     *
     * @return empty if no blocking booking references that appointment — which
     *         is the normal case for an already-cancelled appointment
     */
    Optional<Booking> findBlockingByAppointmentId(AppointmentId appointmentId);

    /**
     * Moves an appointment to a new window: releases the old booking and takes
     * the new one, in <b>one</b> transaction.
     *
     * Two separate calls would be wrong: in between, another request could claim
     * the old slot, and if taking the new window then failed, the old one would
     * already be lost.
     *
     * @return the new booking, already linked to the same appointment
     * @throws SlotConflictException if the new window is taken
     */
    Booking reschedule(AppointmentId appointmentId, SlotRequest newWindow);

    /**
     * Finds reservations that are still {@code PENDING} past their deadline.
     *
     * <p><b>These must not be released blindly.</b> A deadline passing does not
     * abort an in-flight HTTP call and does not prove the appointment is
     * missing. Releasing without checking Graph first produces exactly the
     * double booking the mechanism prevents.
     *
     * @param before cut-off; reservations expiring earlier than this are returned
     */
    List<OrphanedReservation> findOrphaned(Instant before);
}
