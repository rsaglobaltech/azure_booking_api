package com.booking.azure.domain.exception;

import com.booking.azure.domain.model.SlotStatus;
import com.booking.azure.domain.model.vo.BookingId;

/**
 * A transition was attempted that the booking's lifecycle does not allow.
 *
 * <h2>Why this is an exception and not a silent no-op</h2>
 *
 * Confirming a booking whose slots were already released would mean the system
 * believes it holds a slot it has given away — the exact state that produces a
 * double booking. Failing loudly turns a silent data corruption into a visible
 * error.
 */
public class IllegalBookingStateException extends DomainException {

    public IllegalBookingStateException(BookingId bookingId, SlotStatus from, String attempted) {
        super("Booking %s cannot %s while %s".formatted(bookingId, attempted, from));
    }
}
