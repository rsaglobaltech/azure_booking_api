package com.booking.azure.domain.model.vo;

/**
 * Where an appointment physically takes place.
 *
 * All fields are optional: a booking may name a room without an address, or
 * carry no location at all when it happens online.
 */
public record ServiceLocation(
        String displayName,
        String street,
        String city,
        String state,
        String postalCode,
        String countryOrRegion) {

    public boolean isEmpty() {
        return isBlank(displayName) && isBlank(street) && isBlank(city)
                && isBlank(state) && isBlank(postalCode) && isBlank(countryOrRegion);
    }

    public boolean hasAddress() {
        return !isBlank(street) || !isBlank(city) || !isBlank(state)
                || !isBlank(postalCode) || !isBlank(countryOrRegion);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
