package com.booking.azure.domain.model.vo;

/**
 * Local identifier of an agency, assigned by this system's own database.
 *
 * Not to be confused with {@link BusinessId}, which is the identifier the same
 * agency carries inside Microsoft Bookings. Keeping both apart is what allows
 * the Microsoft mapping to change without touching local references.
 */
public record AgencyId(Long value) {

    public AgencyId {
        if (value == null) {
            throw new IllegalArgumentException("agencyId is required");
        }
    }

    public static AgencyId of(Long value) {
        return new AgencyId(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
