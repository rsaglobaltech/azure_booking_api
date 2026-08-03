package com.booking.azure.domain.model.vo;

import java.util.UUID;

/**
 * Identity of the {@code Booking} aggregate.
 *
 * Assigned locally, before Microsoft Graph knows anything about the booking.
 * That is the point: a booking exists — and holds slots — from the moment it is
 * requested, long before it has an {@link AppointmentId}. Domain events raised
 * during that window need something to point at, and this is it.
 *
 * <p>Deliberately a string rather than a {@link UUID}: rows migrated from before
 * the aggregate existed carry a database-generated identifier that is not in
 * UUID form, and rejecting those at load time would be pointless strictness.
 */
public record BookingId(String value) {

    public BookingId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("bookingId is required");
        }
    }

    public static BookingId of(String value) {
        return new BookingId(value);
    }

    public static BookingId generate() {
        return new BookingId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
