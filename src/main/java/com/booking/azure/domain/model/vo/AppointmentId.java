package com.booking.azure.domain.model.vo;

/**
 * Identifier of an appointment that exists in Microsoft Graph.
 *
 * Distinct from a local booking identifier on purpose: this value only exists
 * once Graph has accepted the write. Until then a reservation holds the slot
 * without any appointment id attached.
 */
public record AppointmentId(String value) {

    public AppointmentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("appointmentId is required");
        }
    }

    public static AppointmentId of(String value) {
        return new AppointmentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
