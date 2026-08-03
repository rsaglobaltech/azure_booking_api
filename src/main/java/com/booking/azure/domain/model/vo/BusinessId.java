package com.booking.azure.domain.model.vo;

/**
 * Identifier of a booking business as known to Microsoft Bookings.
 *
 * Wrapping it removes the ambiguity of passing bare strings around: a
 * {@code BusinessId} can no longer be handed to a parameter expecting a
 * {@link ServiceId} or a {@link StaffMemberId}, a mistake the compiler could
 * not catch while everything was a {@code String}.
 */
public record BusinessId(String value) {

    public BusinessId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("businessId is required");
        }
    }

    public static BusinessId of(String value) {
        return new BusinessId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
