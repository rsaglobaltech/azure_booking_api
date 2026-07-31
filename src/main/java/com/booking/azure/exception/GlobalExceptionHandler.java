package com.booking.azure.exception;

import com.booking.azure.domain.exception.GraphResponseException;
import com.booking.azure.domain.exception.GraphUnknownException;
import com.booking.azure.domain.exception.SlotConflictException;
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
     * Der angefragte Slot ist bereits belegt.
     *
     * Muss vor {@link #laufzeitausnahmeBehandeln} greifen – Spring wählt den
     * spezifischsten passenden Behandler, daher genügt die eigene Signatur.
     *
     * Bewusst auf {@code INFO} protokolliert: ein abgewiesener Slot ist der
     * Normalfall bei gleichzeitigen Anfragen, kein Systemfehler.
     *
     * @param ex Die Kollisionsausnahme aus der Slot-Reservierung
     * @return HTTP 409 Conflict
     */
    @ExceptionHandler(SlotConflictException.class)
    public ResponseEntity<Fehlerantwort> slotKollisionBehandeln(SlotConflictException ex) {
        log.info("Slot-Kollision abgewiesen: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Fehlerantwort(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /**
     * Fehlerhafte Eingabewerte, die erst jenseits der Bean-Validation auffallen –
     * etwa eine unbekannte Zeitzone oder ein unlesbarer Zeitstempel.
     *
     * @param ex Die Ausnahme
     * @return HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Fehlerantwort> ungueltigeEingabeBehandeln(IllegalArgumentException ex) {
        log.warn("Ungültige Eingabe: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Fehlerantwort(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * Microsoft Graph hat mit einem Fehlerstatus geantwortet.
     *
     * @param ex Die Ausnahme mit dem Status der Graph-Antwort
     * @return HTTP 502 Bad Gateway
     */
    @ExceptionHandler(GraphResponseException.class)
    public ResponseEntity<Fehlerantwort> graphAntwortfehlerBehandeln(GraphResponseException ex) {
        log.error("Graph antwortete mit Fehlerstatus {}: {}", ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new Fehlerantwort(HttpStatus.BAD_GATEWAY.value(), ex.getMessage()));
    }

    /**
     * Microsoft Graph hat nicht geantwortet – der Ausgang ist unbekannt.
     *
     * <p>{@code 504 Gateway Timeout} statt {@code 502}, weil der Unterschied für
     * den Aufrufer handlungsrelevant ist: bei {@code 502} steht fest, dass nichts
     * angelegt wurde; bei {@code 504} kann der Termin sehr wohl existieren.
     *
     * <p>Eine blinde Wiederholung ist hier <b>nicht</b> sicher, solange die
     * Idempotenz (Phase 1) fehlt. Der Slot bleibt bis zur Prüfung durch den
     * Wiederherstellungsjob belegt, sodass eine Wiederholung derzeit {@code 409}
     * erhält statt einen zweiten Termin anzulegen.
     *
     * @param ex Die Ausnahme
     * @return HTTP 504 Gateway Timeout
     */
    @ExceptionHandler(GraphUnknownException.class)
    public ResponseEntity<Fehlerantwort> graphOhneAntwortBehandeln(GraphUnknownException ex) {
        log.error("Keine Antwort von Graph, Ausgang unbekannt: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new Fehlerantwort(HttpStatus.GATEWAY_TIMEOUT.value(), ex.getMessage()));
    }

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


