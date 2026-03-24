package com.booking.azure.controller;

import com.booking.azure.domain.port.in.DienstVerwaltung;
import com.booking.azure.dto.BookingServiceDto;
import com.booking.azure.dto.request.CreateServiceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-Controller für die Dienstverwaltung einer Buchungsagentur.
 *
 * Onion-Architektur – Präsentationsschicht:
 *   Abhängig ausschließlich vom eingehenden Port {@link DienstVerwaltung}.
 *
 * Dienste können von Kunden über die öffentliche Buchungs-URL gebucht werden:
 *   https://outlook.office.com/book/{agenturName}@midominio.com
 *
 * Endpunkte:
 *   GET    /api/businesses/{businessId}/services               → Alle Dienste auflisten
 *   GET    /api/businesses/{businessId}/services/{serviceId}   → Einen Dienst abrufen
 *   POST   /api/businesses/{businessId}/services               → Dienst erstellen
 *   PUT    /api/businesses/{businessId}/services/{serviceId}   → Dienst aktualisieren
 *   DELETE /api/businesses/{businessId}/services/{serviceId}   → Dienst löschen
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/services")
@RequiredArgsConstructor
public class BookingServiceController {

    /** Eingehender Port – DienstVerwaltung (Onion-Domänenschicht) */
    private final DienstVerwaltung dienstVerwaltung;

    /**
     * Alle Dienste einer Agentur auflisten.
     *
     * @param businessId ID der Agentur
     * @return Liste aller Dienste
     */
    @GetMapping
    public ResponseEntity<List<BookingServiceDto>> diensteAuflisten(
            @PathVariable String businessId) {
        return ResponseEntity.ok(dienstVerwaltung.diensteAuflisten(businessId));
    }

    /**
     * Einen bestimmten Dienst abrufen.
     *
     * @param businessId ID der Agentur
     * @param serviceId  ID (GUID) des Dienstes
     * @return Dienstdaten
     */
    @GetMapping("/{serviceId}")
    public ResponseEntity<BookingServiceDto> dienstAbrufen(
            @PathVariable String businessId,
            @PathVariable String serviceId) {
        return ResponseEntity.ok(dienstVerwaltung.dienstAbrufen(businessId, serviceId));
    }

    /**
     * Neuen Dienst in einer Agentur erstellen.
     *
     * @param businessId ID der Agentur
     * @param anfrage    Validierter Request-Body mit Dienstdaten
     * @return Der erstellte Dienst (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<BookingServiceDto> dienstErstellen(
            @PathVariable String businessId,
            @Valid @RequestBody CreateServiceRequest anfrage) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dienstVerwaltung.dienstErstellen(businessId, anfrage));
    }

    /**
     * Bestehenden Dienst aktualisieren.
     *
     * @param businessId ID der Agentur
     * @param serviceId  ID des Dienstes
     * @param anfrage    Validierter Request-Body mit neuen Daten
     * @return Der aktualisierte Dienst
     */
    @PutMapping("/{serviceId}")
    public ResponseEntity<BookingServiceDto> dienstAktualisieren(
            @PathVariable String businessId,
            @PathVariable String serviceId,
            @Valid @RequestBody CreateServiceRequest anfrage) {
        return ResponseEntity.ok(dienstVerwaltung.dienstAktualisieren(businessId, serviceId, anfrage));
    }

    /**
     * Dienst aus einer Agentur löschen.
     *
     * @param businessId ID der Agentur
     * @param serviceId  ID des Dienstes
     * @return HTTP 204 (kein Inhalt)
     */
    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> dienstLoeschen(
            @PathVariable String businessId,
            @PathVariable String serviceId) {
        dienstVerwaltung.dienstLoeschen(businessId, serviceId);
        return ResponseEntity.noContent().build();
    }
}
