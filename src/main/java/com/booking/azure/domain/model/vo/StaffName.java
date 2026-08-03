package com.booking.azure.domain.model.vo;

/**
 * The human-readable name callers use to refer to a staff member.
 *
 * Callers book "Anna", not the opaque Microsoft identifier. Resolving one into
 * the other is the job of the {@code Agency} aggregate, which is the only place
 * that knows the mapping.
 */
public record StaffName(String value) {

    public StaffName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("staff name is required");
        }
        value = value.trim();
    }

    public static StaffName of(String value) {
        return new StaffName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
