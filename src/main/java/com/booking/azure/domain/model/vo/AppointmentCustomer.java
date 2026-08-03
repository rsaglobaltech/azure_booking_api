package com.booking.azure.domain.model.vo;

/**
 * The customer an appointment is booked for, with the details Microsoft
 * Bookings keeps alongside the contact.
 *
 * {@link CustomerContact} carries the part the domain reasons about — who to
 * write to. The rest is passed through: an existing customer id when the caller
 * knows one, plus phone and notes.
 */
public record AppointmentCustomer(
        CustomerContact contact,
        String customerId,
        String phone,
        String notes) {

    public AppointmentCustomer {
        if (contact == null) {
            throw new IllegalArgumentException("customer contact is required");
        }
    }

    public static AppointmentCustomer of(CustomerContact contact) {
        return new AppointmentCustomer(contact, null, null, null);
    }
}
