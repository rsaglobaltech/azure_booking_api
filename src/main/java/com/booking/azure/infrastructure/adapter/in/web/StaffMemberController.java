package com.booking.azure.infrastructure.adapter.in.web;

import com.booking.azure.domain.port.in.StaffManagement;
import com.booking.azure.dto.BookingStaffMemberDto;
import com.booking.azure.dto.StaffAvailabilityItemDto;
import com.booking.azure.dto.StaffAvailabilityRequestDto;
import com.booking.azure.domain.command.CreateStaffMemberRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-Controller für die Mitarbeiterverwaltung einer Buchungsagentur.
 *
 * Onion-Architektur – Präsentationsschicht:
 *   Abhängig ausschließlich vom eingehenden Port {@link StaffManagement}.
 *
 * Endpunkte:
 *   GET    /api/businesses/{businessId}/staff                    → Alle Mitarbeiter auflisten
 *   GET    /api/businesses/{businessId}/staff/{staffId}          → Mitarbeiter abrufen
 *   POST   /api/businesses/{businessId}/staff                    → Mitarbeiter anlegen
 *   PUT    /api/businesses/{businessId}/staff/{staffId}          → Mitarbeiter aktualisieren
 *   DELETE /api/businesses/{businessId}/staff/{staffId}          → Mitarbeiter entfernen
 *   POST   /api/businesses/{businessId}/staff/availability       → Verfügbarkeit abfragen
 *                                                                   (frei / belegt)
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/staff")
@RequiredArgsConstructor
public class StaffMemberController {

    /** Eingehender Port – StaffManagement (Onion-Domänenschicht) */
    private final StaffManagement staffManagement;

    /**
     * Alle Mitarbeiter einer Agentur auflisten.
     *
     * @param businessId ID der Agentur
     * @return Liste aller Mitarbeiter
     */
    @GetMapping
    public ResponseEntity<List<BookingStaffMemberDto>> listStaffMembers(
            @PathVariable String businessId) {
        return ResponseEntity.ok(staffManagement.listStaffMembers(businessId));
    }

    /**
     * Einen bestimmten Mitarbeiter abrufen.
     *
     * @param businessId ID der Agentur
     * @param staffId    ID (GUID) des Mitarbeiters
     * @return Mitarbeiterdaten
     */
    @GetMapping("/{staffId}")
    public ResponseEntity<BookingStaffMemberDto> getStaffMember(
            @PathVariable String businessId,
            @PathVariable String staffId) {
        return ResponseEntity.ok(staffManagement.getStaffMember(businessId, staffId));
    }

    /**
     * Neuen Mitarbeiter in einer Agentur anlegen.
     *
     * @param businessId ID der Agentur
     * @param request    Validierter Request-Body mit Mitarbeiterdaten
     * @return Der angelegte Mitarbeiter (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<BookingStaffMemberDto> createStaffMember(
            @PathVariable String businessId,
            @Valid @RequestBody CreateStaffMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffManagement.createStaffMember(businessId, request));
    }

    /**
     * Bestehenden Mitarbeiter aktualisieren.
     *
     * @param businessId ID der Agentur
     * @param staffId    ID des Mitarbeiters
     * @param request    Validierter Request-Body mit neuen Daten
     * @return Der aktualisierte Mitarbeiter
     */
    @PutMapping("/{staffId}")
    public ResponseEntity<BookingStaffMemberDto> updateStaffMember(
            @PathVariable String businessId,
            @PathVariable String staffId,
            @Valid @RequestBody CreateStaffMemberRequest request) {
        return ResponseEntity.ok(
                staffManagement.updateStaffMember(businessId, staffId, request));
    }

    /**
     * Mitarbeiter aus einer Agentur entfernen.
     *
     * @param businessId ID der Agentur
     * @param staffId    ID des Mitarbeiters
     * @return HTTP 204 (kein Inhalt)
     */
    @DeleteMapping("/{staffId}")
    public ResponseEntity<Void> deleteStaffMember(
            @PathVariable String businessId,
            @PathVariable String staffId) {
        staffManagement.deleteStaffMember(businessId, staffId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verfügbarkeit (frei / belegt) eines oder mehrerer Mitarbeiter abfragen.
     *
     * Verfügbarkeitsstatus pro Zeitfenster:
     *   - {@code Available}      → Mitarbeiter ist frei
     *   - {@code Busy}           → Mitarbeiter hat einen Termin (serviceId enthält den Dienst)
     *   - {@code SlotsAvailable} → Freie Zeitfenster vorhanden
     *   - {@code OutOfOffice}    → Mitarbeiter ist außer Haus
     *
     * Request-Body-Beispiel:
     * <pre>
     * {
     *   "staffIds": ["guid-1", "guid-2"],
     *   "startDateTime": { "dateTime": "2024-06-01T08:00:00", "timeZone": "Europe/Berlin" },
     *   "endDateTime":   { "dateTime": "2024-06-01T18:00:00", "timeZone": "Europe/Berlin" }
     * }
     * </pre>
     *
     * @param businessId ID der Agentur
     * @param request    Anfrage mit Mitarbeiter-IDs und Zeitraum
     * @return Verfügbarkeitsliste pro Mitarbeiter
     */
    @PostMapping("/availability")
    public ResponseEntity<List<StaffAvailabilityItemDto>> getStaffMemberAvailability(
            @PathVariable String businessId,
            @RequestBody StaffAvailabilityRequestDto request) {
        return ResponseEntity.ok(
                staffManagement.getStaffMemberAvailability(businessId, request));
    }
}


