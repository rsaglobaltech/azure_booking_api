package com.booking.azure.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Globaler Ausnahmebehandler für alle REST-Controller.
 *
 * Onion-Architektur – Präsentationsschicht:
 *   Konvertiert Domänen- und Infrastrukturausnahmen in standardisierte
 *   HTTP-Fehlerantworten.
 *
 * Behandelte Ausnahmen:
 *   - {@link RuntimeException}                  → HTTP 502 (Graph-API-Fehler)
 *   - {@link MethodArgumentNotValidException}    → HTTP 400 (Validierungsfehler)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Behandelt Laufzeitausnahmen, die typischerweise von der Graph-API
     * oder der Authentifizierung ausgelöst werden.
     *
     * @param ex Die aufgetretene Laufzeitausnahme
     * @return HTTP 502 mit Fehlermeldung
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Fehlerantwort> laufzeitausnahmeBehandeln(RuntimeException ex) {
        log.error("Laufzeitfehler aufgetreten: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new Fehlerantwort(HttpStatus.BAD_GATEWAY.value(), ex.getMessage()));
    }

    /**
     * Behandelt Validierungsfehler bei Request-Bodies (z. B. fehlende Pflichtfelder).
     *
     * @param ex Die Validierungsausnahme
     * @return HTTP 400 mit detaillierten Feldfehlern
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Fehlerantwort> validierungsausnahmeBehandeln(
            MethodArgumentNotValidException ex) {
        String fehler = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Fehlerantwort(HttpStatus.BAD_REQUEST.value(),
                        "Validierung fehlgeschlagen: " + fehler));
    }

    /**
     * Standardisierte Fehlerantwort-Struktur.
     *
     * @param status  HTTP-Statuscode
     * @param nachricht Fehlerbeschreibung
     */
    public record Fehlerantwort(int status, String nachricht) {
        /** Zeitstempel der Fehlerantwort (ISO-8601-Format) */
        public String zeitstempel() {
            return LocalDateTime.now().toString();
        }
    }
}
