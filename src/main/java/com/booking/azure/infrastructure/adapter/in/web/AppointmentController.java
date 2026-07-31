package com.booking.azure.infrastructure.adapter.in.web;

import com.booking.azure.domain.port.in.AppointmentManagement;
import com.booking.azure.dto.BookingAppointmentDto;
import com.booking.azure.domain.command.CreateAppointmentRequest;
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
 *   Abhängig ausschließlich vom eingehenden Port {@link AppointmentManagement}.
 *   Kennt keine konkreten Service-Implementierungen.
 *
 * Endpunkte:
 *   GET    /api/betriebe/{businessId}/termine                       → Alle Termine auflisten
 *   GET    /api/betriebe/{businessId}/termine/kalender              → Kalenderansicht (Zeitraum)
 *   GET    /api/betriebe/{businessId}/termine/{appointmentId}            → Einzelnen Termin abrufen
 *   POST   /api/betriebe/{businessId}/termine                       → Termin erstellen
 *   PUT    /api/betriebe/{businessId}/termine/{appointmentId}            → Termin aktualisieren
 *   DELETE /api/betriebe/{businessId}/termine/{appointmentId}            → Termin stornieren
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    /** Eingehender Port – AppointmentManagement (Onion-Domänenschicht) */
    private final AppointmentManagement appointmentManagement;

    /**
     * Alle Termine eines Buchungsbetriebs auflisten.
     *
     * @param businessId ID der Agentur (z. B. agency@midominio.com)
     * @return Liste aller Termine
     */
    @GetMapping
    public ResponseEntity<List<BookingAppointmentDto>> listAppointments(
            @PathVariable String businessId) {
        return ResponseEntity.ok(appointmentManagement.listAppointments(businessId));
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
                appointmentManagement.kalenderAnsichtAbrufen(businessId, startDateTime, endDateTime));
    }

    /**
     * Einen einzelnen Termin abrufen.
     *
     * @param businessId ID der Agentur
     * @param appointmentId  ID (GUID) des Termins
     * @return Termindaten
     */
    @GetMapping("/{appointmentId}")
    public ResponseEntity<BookingAppointmentDto> getAppointment(
            @PathVariable String businessId,
            @PathVariable String appointmentId) {
        return ResponseEntity.ok(appointmentManagement.getAppointment(businessId, appointmentId));
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
     * @param request    Validierter Request-Body mit Termindaten
     * @return Der erstellte Termin (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<BookingAppointmentDto> createAppointment(
            @PathVariable String businessId,
            @Valid @RequestBody CreateAppointmentRequest request) {
        BookingAppointmentDto created = appointmentManagement.createAppointment(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Bestehenden Termin aktualisieren.
     *
     * @param businessId ID der Agentur
     * @param appointmentId  ID des zu aktualisierenden Termins
     * @param request    Validierter Request-Body mit neuen Termindaten
     * @return Der aktualisierte Termin
     */
    @PutMapping("/{appointmentId}")
    public ResponseEntity<BookingAppointmentDto> updateAppointment(
            @PathVariable String businessId,
            @PathVariable String appointmentId,
            @Valid @RequestBody CreateAppointmentRequest request) {
        BookingAppointmentDto updated = appointmentManagement.updateAppointment(businessId, appointmentId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Termin stornieren / löschen.
     *
     * @param businessId ID der Agentur
     * @param appointmentId  ID des zu stornierenden Termins
     * @return HTTP 204 (kein Inhalt)
     */
    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<Void> cancelAppointment(
            @PathVariable String businessId,
            @PathVariable String appointmentId) {
        appointmentManagement.cancelAppointment(businessId, appointmentId);
        return ResponseEntity.noContent().build();
    }
}


