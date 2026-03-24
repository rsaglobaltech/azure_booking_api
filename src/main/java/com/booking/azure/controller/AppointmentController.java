package com.booking.azure.controller;

import com.booking.azure.domain.port.in.TerminVerwaltung;
import com.booking.azure.dto.BookingAppointmentDto;
import com.booking.azure.dto.request.CreateAppointmentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-Controller für die Terminverwaltung einer Buchungsagentur.
 *
 * Onion-Architektur – Präsentationsschicht:
 *   Abhängig ausschließlich vom eingehenden Port {@link TerminVerwaltung}.
 *   Kennt keine konkreten Service-Implementierungen.
 *
 * Endpunkte:
 *   GET    /api/betriebe/{betriebId}/termine                       → Alle Termine auflisten
 *   GET    /api/betriebe/{betriebId}/termine/kalender              → Kalenderansicht (Zeitraum)
 *   GET    /api/betriebe/{betriebId}/termine/{terminId}            → Einzelnen Termin abrufen
 *   POST   /api/betriebe/{betriebId}/termine                       → Termin erstellen
 *   PUT    /api/betriebe/{betriebId}/termine/{terminId}            → Termin aktualisieren
 *   DELETE /api/betriebe/{betriebId}/termine/{terminId}            → Termin stornieren
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    /** Eingehender Port – TerminVerwaltung (Onion-Domänenschicht) */
    private final TerminVerwaltung terminVerwaltung;

    /**
     * Alle Termine eines Buchungsbetriebs auflisten.
     *
     * @param businessId ID der Agentur (z. B. agentur@midominio.com)
     * @return Liste aller Termine
     */
    @GetMapping
    public ResponseEntity<List<BookingAppointmentDto>> termineAuflisten(
            @PathVariable String businessId) {
        return ResponseEntity.ok(terminVerwaltung.termineAuflisten(businessId));
    }

    /**
     * Kalenderansicht: Termine in einem Datumszeitraum abrufen.
     *
     * Abfrageparameter:
     *   - startDateTime: Startdatum im ISO-8601-Format (z. B. 2024-06-01T00:00:00Z)
     *   - endDateTime:   Enddatum im ISO-8601-Format
     *
     * @param businessId     ID der Agentur
     * @param startDateTime  Startdatum und -zeit
     * @param endDateTime    Enddatum und -zeit
     * @return Gefilterte Terminliste
     */
    @GetMapping("/calendar")
    public ResponseEntity<List<BookingAppointmentDto>> kalenderAnsichtAbrufen(
            @PathVariable String businessId,
            @RequestParam String startDateTime,
            @RequestParam String endDateTime) {
        return ResponseEntity.ok(
                terminVerwaltung.kalenderAnsichtAbrufen(businessId, startDateTime, endDateTime));
    }

    /**
     * Einen einzelnen Termin abrufen.
     *
     * @param businessId ID der Agentur
     * @param appointmentId  ID (GUID) des Termins
     * @return Termindaten
     */
    @GetMapping("/{appointmentId}")
    public ResponseEntity<BookingAppointmentDto> terminAbrufen(
            @PathVariable String businessId,
            @PathVariable String appointmentId) {
        return ResponseEntity.ok(terminVerwaltung.terminAbrufen(businessId, appointmentId));
    }

    /**
     * Neuen Termin erstellen.
     *
     * Pflichtfelder im Request-Body:
     *   - serviceId:      ID (GUID) des gebuchten Dienstes
     *   - startDateTime:  Startzeit mit Zeitzone (z. B. Europe/Berlin)
     *   - endDateTime:    Endzeit mit Zeitzone
     *
     * Optionale Felder:
     *   - staffMemberIds: Zugewiesene Mitarbeiter-IDs
     *   - customers:      Kundendaten (Name, E-Mail, Telefon)
     *   - isLocationOnline: true = Teams-Besprechungslink erzeugen
     *
     * @param businessId ID der Agentur
     * @param anfrage    Validierter Request-Body mit Termindaten
     * @return Der erstellte Termin (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<BookingAppointmentDto> terminErstellen(
            @PathVariable String businessId,
            @Valid @RequestBody CreateAppointmentRequest anfrage) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(terminVerwaltung.terminErstellen(businessId, anfrage));
    }

    /**
     * Bestehenden Termin aktualisieren.
     *
     * @param businessId ID der Agentur
     * @param appointmentId  ID des zu aktualisierenden Termins
     * @param anfrage    Validierter Request-Body mit neuen Termindaten
     * @return Der aktualisierte Termin
     */
    @PutMapping("/{appointmentId}")
    public ResponseEntity<BookingAppointmentDto> terminAktualisieren(
            @PathVariable String businessId,
            @PathVariable String appointmentId,
            @Valid @RequestBody CreateAppointmentRequest anfrage) {
        return ResponseEntity.ok(
                terminVerwaltung.terminAktualisieren(businessId, appointmentId, anfrage));
    }

    /**
     * Termin stornieren / löschen.
     *
     * @param businessId ID der Agentur
     * @param appointmentId  ID des zu stornierenden Termins
     * @return HTTP 204 (kein Inhalt)
     */
    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<Void> terminStornieren(
            @PathVariable String businessId,
            @PathVariable String appointmentId) {
        terminVerwaltung.terminStornieren(businessId, appointmentId);
        return ResponseEntity.noContent().build();
    }
}
