package com.booking.azure.domain.model.vo;

/**
 * The human-readable name callers use to refer to an agency.
 *
 * This is the name that appears in the public API; the {@code Agency} aggregate
 * translates it into the identifiers Microsoft Bookings expects.
 */
public record AgencyName(String value) {

    public AgencyName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("agency name is required");
        }
        value = value.trim();
    }

    public static AgencyName of(String value) {
        return new AgencyName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
