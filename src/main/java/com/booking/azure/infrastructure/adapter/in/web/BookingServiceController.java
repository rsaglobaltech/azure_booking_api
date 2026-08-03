package com.booking.azure.infrastructure.adapter.in.web;

import com.booking.azure.application.port.in.ServiceManagement;
import com.booking.azure.dto.BookingServiceDto;
import com.booking.azure.application.command.CreateServiceRequest;
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
 *   Abhängig ausschließlich vom eingehenden Port {@link ServiceManagement}.
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

    /** Eingehender Port – ServiceManagement (Onion-Domänenschicht) */
    private final ServiceManagement serviceManagement;

    /**
     * Alle Dienste einer Agentur auflisten.
     *
     * @param businessId ID der Agentur
     * @return Liste aller Dienste
     */
    @GetMapping
    public ResponseEntity<List<BookingServiceDto>> listServices(
            @PathVariable String businessId) {
        return ResponseEntity.ok(serviceManagement.listServices(businessId));
    }

    /**
     * Einen bestimmten Dienst abrufen.
     *
     * @param businessId ID der Agentur
     * @param serviceId  ID (GUID) des Dienstes
     * @return Dienstdaten
     */
    @GetMapping("/{serviceId}")
    public ResponseEntity<BookingServiceDto> getService(
            @PathVariable String businessId,
            @PathVariable String serviceId) {
        return ResponseEntity.ok(serviceManagement.getService(businessId, serviceId));
    }

    /**
     * Neuen Dienst in einer Agentur erstellen.
     *
     * @param businessId ID der Agentur
     * @param request    Validierter Request-Body mit Dienstdaten
     * @return Der erstellte Dienst (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<BookingServiceDto> createService(
            @PathVariable String businessId,
            @Valid @RequestBody CreateServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceManagement.createService(businessId, request));
    }

    /**
     * Bestehenden Dienst aktualisieren.
     *
     * @param businessId ID der Agentur
     * @param serviceId  ID des Dienstes
     * @param request    Validierter Request-Body mit neuen Daten
     * @return Der aktualisierte Dienst
     */
    @PutMapping("/{serviceId}")
    public ResponseEntity<BookingServiceDto> updateService(
            @PathVariable String businessId,
            @PathVariable String serviceId,
            @Valid @RequestBody CreateServiceRequest request) {
        return ResponseEntity.ok(serviceManagement.updateService(businessId, serviceId, request));
    }

    /**
     * Dienst aus einer Agentur löschen.
     *
     * @param businessId ID der Agentur
     * @param serviceId  ID des Dienstes
     * @return HTTP 204 (kein Inhalt)
     */
    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(
            @PathVariable String businessId,
            @PathVariable String serviceId) {
        serviceManagement.deleteService(businessId, serviceId);
        return ResponseEntity.noContent().build();
    }
}


