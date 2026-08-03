package com.booking.azure.exception;

import com.booking.azure.domain.exception.AgencyNotFoundException;
import com.booking.azure.domain.exception.DomainException;
import com.booking.azure.domain.exception.GraphResponseException;
import com.booking.azure.domain.exception.GraphUnknownException;
import com.booking.azure.domain.exception.SlotConflictException;
import com.booking.azure.domain.exception.StaffMemberNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Turns domain and infrastructure failures into HTTP responses.
 *
 * Presentation layer: this is the only place that decides what a business
 * failure means over HTTP. The domain raises exceptions that carry no transport
 * meaning at all.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * The requested slot is already taken.
     *
     * Must win over {@link #handleRuntimeException} — Spring picks the most
     * specific matching handler, so the signature is enough.
     *
     * <p>Logged at {@code INFO} on purpose: a rejected slot is the normal
     * outcome of concurrent requests, not a system fault.
     *
     * @return HTTP 409 Conflict
     */
    @ExceptionHandler(SlotConflictException.class)
    public ResponseEntity<Fehlerantwort> handleSlotConflict(SlotConflictException ex) {
        log.info("Slot conflict rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Fehlerantwort(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /**
     * A requested agency or staff member does not exist.
     *
     * The application layer used to raise these as {@code ResponseStatusException}
     * itself. Translating a business failure into a status code belongs here,
     * not inside the use case.
     *
     * @return HTTP 404 Not Found
     */
    @ExceptionHandler({AgencyNotFoundException.class, StaffMemberNotFoundException.class})
    public ResponseEntity<Fehlerantwort> handleNotFound(DomainException ex) {
        log.info("Not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Fehlerantwort(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /**
     * Bad input that only surfaces past bean validation — an unknown time zone,
     * an unreadable timestamp, a value object rejecting its arguments.
     *
     * @return HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Fehlerantwort> handleInvalidInput(IllegalArgumentException ex) {
        log.warn("Invalid input: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Fehlerantwort(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * Microsoft Graph answered with an error status.
     *
     * @return HTTP 502 Bad Gateway
     */
    @ExceptionHandler(GraphResponseException.class)
    public ResponseEntity<Fehlerantwort> handleGraphErrorStatus(GraphResponseException ex) {
        log.error("Graph answered with error status {}: {}", ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new Fehlerantwort(HttpStatus.BAD_GATEWAY.value(), ex.getMessage()));
    }

    /**
     * Microsoft Graph did not answer — the outcome is unknown.
     *
     * <p>{@code 504 Gateway Timeout} rather than {@code 502}, because the
     * difference is actionable for the caller: with {@code 502} it is certain
     * nothing was created; with {@code 504} the appointment may well exist.
     *
     * <p>A blind retry is <b>not</b> safe here while idempotency is missing. The
     * slot stays held until the recovery job checks it, so a retry currently
     * receives {@code 409} instead of creating a second appointment.
     *
     * @return HTTP 504 Gateway Timeout
     */
    @ExceptionHandler(GraphUnknownException.class)
    public ResponseEntity<Fehlerantwort> handleGraphNoResponse(GraphUnknownException ex) {
        log.error("No answer from Graph, outcome unknown: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new Fehlerantwort(HttpStatus.GATEWAY_TIMEOUT.value(), ex.getMessage()));
    }

    /**
     * Anything else, typically raised by the Graph API or by authentication.
     *
     * @return HTTP 502 Bad Gateway
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Fehlerantwort> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new Fehlerantwort(HttpStatus.BAD_GATEWAY.value(), ex.getMessage()));
    }

    /**
     * Request body validation failures, such as missing mandatory fields.
     *
     * @return HTTP 400 with the offending fields
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Fehlerantwort> handleValidationException(
            MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Fehlerantwort(HttpStatus.BAD_REQUEST.value(),
                        "Validation failed: " + details));
    }

    /**
     * The error body every failing request returns.
     *
     * <p><b>The German component names are load-bearing and must not be renamed
     * casually.</b> Jackson serialises a record by its component names, so this
     * type emits {@code {"status":…,"nachricht":…,"zeitstempel":…}} — the shape
     * every existing client already parses. Renaming {@code nachricht} to
     * {@code message} silently breaks them, so it stayed German while the rest
     * of the code moved to English. Changing it is an API version decision, not
     * a refactor.
     *
     * @param status    HTTP status code
     * @param nachricht human-readable description of the failure
     */
    public record Fehlerantwort(int status, String nachricht) {
        /** Timestamp of the error response, ISO-8601. */
        public String zeitstempel() {
            return LocalDateTime.now().toString();
        }
    }
}
