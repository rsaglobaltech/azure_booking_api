package com.booking.azure.domain.exception;

/**
 * Base class for every failure the domain itself can express.
 *
 * <h2>Why this exists</h2>
 *
 * The application layer used to throw {@code ResponseStatusException} — a Spring
 * web type — when an agency or a staff member could not be found. That made the
 * use case unusable outside an HTTP request and put a presentation concern in
 * the middle of business logic. Domain failures now carry no transport meaning;
 * translating them into status codes is the job of the presentation layer.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
