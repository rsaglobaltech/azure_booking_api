package com.booking.azure.controller;

import com.booking.azure.domain.port.in.AgenturVerwaltung;
import com.booking.azure.dto.BookingBusinessDto;
import com.booking.azure.dto.request.CreateBookingBusinessRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-Controller für die Agenturverwaltung (Buchungsbetriebe).
 *
 * Onion-Architektur – Präsentationsschicht:
 *   Abhängig ausschließlich vom eingehenden Port {@link AgenturVerwaltung}.
 *
 * Jede Agentur hat eine eindeutige, dynamische Buchungs-URL:
 *   https://outlook.office.com/book/{agenturName}@midominio.com
 *
 * Endpunkte:
 *   GET    /api/businesses                            → Alle Agenturen des Mandanten
 *   GET    /api/businesses/{businessId}               → Eine Agentur abrufen
 *   POST   /api/businesses                            → Neue Agentur erstellen
 *   PUT    /api/businesses/{businessId}               → Agentur aktualisieren
 *   DELETE /api/businesses/{businessId}               → Agentur löschen
 *   POST   /api/businesses/{businessId}/publish       → Buchungsseite veröffentlichen
 *   POST   /api/businesses/{businessId}/unpublish     → Buchungsseite deaktivieren
 */
@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
public class BookingBusinessController {

    /** Eingehender Port – AgenturVerwaltung (Onion-Domänenschicht) */
    private final AgenturVerwaltung agenturVerwaltung;

    /**
     * Alle Buchungsbetriebe (Agenturen) des Mandanten auflisten.
     * Jeder Betrieb enthält die dynamische Buchungs-URL.
     *
     * @return Liste aller Buchungsbetriebe
     */
    @GetMapping
    public ResponseEntity<List<BookingBusinessDto>> betriebeAuflisten() {
        return ResponseEntity.ok(agenturVerwaltung.betriebeAuflisten());
    }

    /**
     * Einen bestimmten Buchungsbetrieb abrufen.
     * Die Buchungs-URL wird dynamisch berechnet:
     * {@code https://outlook.office.com/book/{agenturName}@midominio.com}
     *
     * @param businessId ID der Agentur (dynamischer Agenturname)
     * @return Betriebsdaten inkl. Buchungs-URL
     */
    @GetMapping("/{businessId}")
    public ResponseEntity<BookingBusinessDto> betriebAbrufen(@PathVariable String businessId) {
        return ResponseEntity.ok(agenturVerwaltung.betriebAbrufen(businessId));
    }

    /**
     * Neuen Buchungsbetrieb (Agentur) erstellen.
     *
     * @param anfrage Validierter Request-Body mit Betriebsdaten
     * @return Der erstellte Betrieb (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<BookingBusinessDto> betriebErstellen(
            @Valid @RequestBody CreateBookingBusinessRequest anfrage) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agenturVerwaltung.betriebErstellen(anfrage));
    }

    /**
     * Bestehenden Buchungsbetrieb aktualisieren.
     *
     * @param businessId ID der Agentur
     * @param anfrage    Validierter Request-Body mit neuen Daten
     * @return Der aktualisierte Betrieb
     */
    @PutMapping("/{businessId}")
    public ResponseEntity<BookingBusinessDto> betriebAktualisieren(
            @PathVariable String businessId,
            @Valid @RequestBody CreateBookingBusinessRequest anfrage) {
        return ResponseEntity.ok(agenturVerwaltung.betriebAktualisieren(businessId, anfrage));
    }

    /**
     * Buchungsbetrieb dauerhaft löschen.
     *
     * @param businessId ID der Agentur
     * @return HTTP 204 (kein Inhalt)
     */
    @DeleteMapping("/{businessId}")
    public ResponseEntity<Void> betriebLoeschen(@PathVariable String businessId) {
        agenturVerwaltung.betriebLoeschen(businessId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Buchungsseite einer Agentur veröffentlichen.
     * Nach der Veröffentlichung ist die Seite erreichbar unter:
     * {@code https://outlook.office.com/book/{agenturName}@midominio.com}
     *
     * @param businessId ID der Agentur
     * @return HTTP 200
     */
    @PostMapping("/{businessId}/publish")
    public ResponseEntity<Void> betriebVeroeffentlichen(@PathVariable String businessId) {
        agenturVerwaltung.betriebVeroeffentlichen(businessId);
        return ResponseEntity.ok().build();
    }

    /**
     * Buchungsseite einer Agentur deaktivieren.
     *
     * @param businessId ID der Agentur
     * @return HTTP 200
     */
    @PostMapping("/{businessId}/unpublish")
    public ResponseEntity<Void> betriebDeaktivieren(@PathVariable String businessId) {
        agenturVerwaltung.betriebDeaktivieren(businessId);
        return ResponseEntity.ok().build();
    }
}
