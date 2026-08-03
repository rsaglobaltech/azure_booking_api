package com.booking.azure.domain.model.vo;

/**
 * Identifier of a staff member as known to Microsoft Bookings.
 *
 * This is the technical id, not the human-readable name the callers use. The
 * translation between the two is the responsibility of the {@code Agency}
 * aggregate.
 */
public record StaffMemberId(String value) {

    public StaffMemberId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("staffMemberId is required");
        }
    }

    public static StaffMemberId of(String value) {
        return new StaffMemberId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
