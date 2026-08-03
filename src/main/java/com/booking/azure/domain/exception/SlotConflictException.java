package com.booking.azure.domain.exception;

/**
 * The requested time window is already taken for at least one staff member.
 *
 * Domain layer: a business failure, independent of HTTP. Mapping it onto
 * {@code 409 Conflict} happens in the presentation layer's
 * {@code GlobalExceptionHandler}.
 *
 * <p>Raised when the overlap check rejects the insert — that is, exactly when a
 * concurrent or earlier request has already won the slot.
 */
public class SlotConflictException extends DomainException {

    public SlotConflictException(String message) {
        super(message);
    }

    public SlotConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
