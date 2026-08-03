package com.booking.azure.domain.model.vo;

/**
 * Name and email address of the person a booking is made for.
 *
 * Grouping the two removes the defensive null-and-empty juggling that used to
 * surround every read of the customer list, where a missing customer silently
 * became the string {@code "Unknown"} in the middle of building a notification.
 * A booking either has a customer contact or it does not.
 */
public record CustomerContact(String name, String email) {

    public CustomerContact {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("customer name is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("customer email is required");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("customer email is not an address: " + email);
        }
        name = name.trim();
        email = email.trim();
    }

    public static CustomerContact of(String name, String email) {
        return new CustomerContact(name, email);
    }
}
