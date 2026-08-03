package com.booking.azure.domain.model;

import com.booking.azure.domain.model.vo.BookingId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;

/**
 * A {@code PENDING} reservation whose deadline has passed.
 *
 * Appears when something went wrong between committing the reservation and
 * hearing back from Microsoft Graph: a timeout, a dropped connection, or an
 * instance crashing.
 *
 * <p><b>The state says nothing about whether the appointment exists.</b> That is
 * precisely why Graph must be consulted before any decision is taken — see
 * {@code SlotRecoveryService}.
 *
 * @param id            identifier of the reservation row
 * @param bookingId     the aggregate this reservation belongs to; recovery
 *                      loads the whole booking through it, so a decision
 *                      applies to every slot the booking holds rather than to
 *                      one row in isolation
 * @param businessId    the booking business
 * @param serviceId     the service
 * @param staffMemberId the staff member whose slot is held
 * @param window        the held window, UTC
 */
public record OrphanedReservation(
        Long id,
        BookingId bookingId,
        BusinessId businessId,
        ServiceId serviceId,
        StaffMemberId staffMemberId,
        TimeWindow window) {
}
