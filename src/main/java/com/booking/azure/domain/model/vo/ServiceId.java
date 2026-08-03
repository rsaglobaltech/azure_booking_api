package com.booking.azure.domain.model.vo;

/** Identifier of a bookable service offered by an agency. */
public record ServiceId(String value) {

    public ServiceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
    }

    public static ServiceId of(String value) {
        return new ServiceId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
