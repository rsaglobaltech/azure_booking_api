package com.booking.azure.service;

import com.booking.azure.domain.port.in.MitarbeiterVerwaltung;
import com.booking.azure.domain.port.out.GraphApiAnfrage;
import com.booking.azure.dto.*;
import com.booking.azure.dto.request.CreateStaffMemberRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anwendungsdienst für die Mitarbeiterverwaltung eines Buchungsbetriebs.
 *
 * Onion-Architektur – Anwendungsschicht:
 *   Implementiert den eingehenden Port {@link MitarbeiterVerwaltung}.
 *   Abhängig vom ausgehenden Port {@link GraphApiAnfrage}.
 *
 * Mitarbeiter können Termine annehmen. Ihre Verfügbarkeit wird über
 * die Microsoft-Graph-API verwaltet. Mögliche Verfügbarkeitsstatus:
 *   - {@code Available}      → Mitarbeiter ist verfügbar
 *   - {@code Busy}           → Mitarbeiter hat einen laufenden Termin
 *   - {@code SlotsAvailable} → Freie Zeitfenster vorhanden
 *   - {@code OutOfOffice}    → Mitarbeiter ist außer Haus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffMemberService implements MitarbeiterVerwaltung {

    /** Ausgehender Port – wird durch den GraphApiClient implementiert */
    private final GraphApiAnfrage graphApiAnfrage;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String mitarbeiterPfad(String betriebId) {
        return "/solutions/bookingBusinesses/" + betriebId + "/staffMembers";
    }

    /**
     * Alle Mitarbeiter eines Buchungsbetriebs auflisten.
     *
     * @param betriebId ID der Agentur
     * @return Liste aller Mitarbeiter
     */
    @Override
    public List<BookingStaffMemberDto> mitarbeiterAuflisten(String betriebId) {
        log.info("Mitarbeiter werden aufgelistet für Betrieb: {}", betriebId);
        GraphListResponse<BookingStaffMemberDto> antwort = graphApiAnfrage.get(
                mitarbeiterPfad(betriebId), GraphListResponse.class);
        return listeMappen(antwort.getValue(), BookingStaffMemberDto.class);
    }

    /**
     * Einen bestimmten Mitarbeiter abrufen.
     *
     * @param betriebId     ID der Agentur
     * @param mitarbeiterId ID (GUID) des Mitarbeiters
     * @return Mitarbeiterdaten
     */
    @Override
    public BookingStaffMemberDto mitarbeiterAbrufen(String betriebId, String mitarbeiterId) {
        log.info("Mitarbeiter {} wird abgerufen für Betrieb {}", mitarbeiterId, betriebId);
        return graphApiAnfrage.get(mitarbeiterPfad(betriebId) + "/" + mitarbeiterId,
                BookingStaffMemberDto.class);
    }

    /**
     * Neuen Mitarbeiter in einem Buchungsbetrieb anlegen.
     *
     * @param betriebId ID der Agentur
     * @param anfrage   Mitarbeiterdaten (Name, E-Mail, Rolle, Zeitzone)
     * @return Der erstellte Mitarbeiter
     */
    @Override
    public BookingStaffMemberDto mitarbeiterErstellen(String betriebId,
                                                      CreateStaffMemberRequest anfrage) {
        log.info("Neuer Mitarbeiter '{}' wird angelegt in Betrieb {}", anfrage.getDisplayName(), betriebId);
        return graphApiAnfrage.post(mitarbeiterPfad(betriebId), anfrage, BookingStaffMemberDto.class);
    }

    /**
     * Bestehenden Mitarbeiter aktualisieren.
     *
     * @param betriebId     ID der Agentur
     * @param mitarbeiterId ID des Mitarbeiters
     * @param anfrage       Neue Mitarbeiterdaten
     * @return Der aktualisierte Mitarbeiter
     */
    @Override
    public BookingStaffMemberDto mitarbeiterAktualisieren(String betriebId,
                                                          String mitarbeiterId,
                                                          CreateStaffMemberRequest anfrage) {
        log.info("Mitarbeiter {} wird aktualisiert in Betrieb {}", mitarbeiterId, betriebId);
        return graphApiAnfrage.patch(mitarbeiterPfad(betriebId) + "/" + mitarbeiterId,
                anfrage, BookingStaffMemberDto.class);
    }

    /**
     * Mitarbeiter aus einem Buchungsbetrieb entfernen.
     *
     * @param betriebId     ID der Agentur
     * @param mitarbeiterId ID des zu entfernenden Mitarbeiters
     */
    @Override
    public void mitarbeiterLoeschen(String betriebId, String mitarbeiterId) {
        log.info("Mitarbeiter {} wird entfernt aus Betrieb {}", mitarbeiterId, betriebId);
        graphApiAnfrage.delete(mitarbeiterPfad(betriebId) + "/" + mitarbeiterId);
    }

    /**
     * Verfügbarkeit (frei / belegt) eines oder mehrerer Mitarbeiter
     * in einem Zeitraum abfragen.
     *
     * Verfügbarkeitsstatus pro Zeitfenster:
     * - {@code Available}      → Mitarbeiter ist frei
     * - {@code Busy}           → Mitarbeiter hat einen Termin (siehe serviceId)
     * - {@code SlotsAvailable} → Freie Zeitfenster vorhanden
     * - {@code OutOfOffice}    → Mitarbeiter ist außer Haus
     *
     * @param betriebId ID der Agentur
     * @param anfrage   Anfrage mit Mitarbeiter-IDs und Zeitraum
     * @return Verfügbarkeitsliste pro Mitarbeiter
     */
    @Override
    public List<StaffAvailabilityItemDto> mitarbeiterVerfuegbarkeitAbrufen(
            String betriebId, StaffAvailabilityRequestDto anfrage) {
        log.info("Mitarbeiterverfügbarkeit wird abgefragt in Betrieb {}: mitarbeiterIds={}",
                betriebId, anfrage.getStaffIds());

        String pfad = "/solutions/bookingBusinesses/" + betriebId + "/getStaffAvailability";
        StaffAvailabilityResponseDto antwort = graphApiAnfrage.post(pfad, anfrage,
                StaffAvailabilityResponseDto.class);

        return antwort != null ? antwort.getStaffAvailabilityItem() : List.of();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> listeMappen(List<?> rohliste, Class<T> zielklasse) {
        if (rohliste == null) return List.of();
        return objectMapper.convertValue(rohliste,
                objectMapper.getTypeFactory().constructCollectionType(List.class, zielklasse));
    }
}
