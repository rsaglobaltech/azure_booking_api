package com.booking.azure.domain.port.in;

import com.booking.azure.dto.BookingStaffMemberDto;
import com.booking.azure.dto.StaffAvailabilityItemDto;
import com.booking.azure.dto.StaffAvailabilityRequestDto;
import com.booking.azure.dto.request.CreateStaffMemberRequest;

import java.util.List;

/**
 * Eingehender Port (Use-Case-Interface) für die Mitarbeiterverwaltung.
 *
 * Onion-Architektur – Domänenschicht:
 *   Definiert alle Use Cases rund um Mitarbeiter (Staff Members) eines
 *   Buchungsbetriebs (Agentur). Mitarbeiter können Termine annehmen
 *   und ihre Verfügbarkeit (frei / belegt) wird über Microsoft Bookings
 *   verwaltet.
 *
 * Verfügbarkeitsstatus:
 *   - {@code Available}      → Mitarbeiter ist verfügbar
 *   - {@code Busy}           → Mitarbeiter hat einen Termin
 *   - {@code SlotsAvailable} → Es gibt freie Zeitfenster
 *   - {@code OutOfOffice}    → Mitarbeiter ist außer Haus
 */
public interface MitarbeiterVerwaltung {

    /**
     * Alle Mitarbeiter eines Buchungsbetriebs auflisten.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @return Liste aller Mitarbeiter
     */
    List<BookingStaffMemberDto> mitarbeiterAuflisten(String betriebId);

    /**
     * Einen bestimmten Mitarbeiter abrufen.
     *
     * @param betriebId    ID des Buchungsbetriebs
     * @param mitarbeiterId ID (GUID) des Mitarbeiters
     * @return Mitarbeiterdaten
     */
    BookingStaffMemberDto mitarbeiterAbrufen(String betriebId, String mitarbeiterId);

    /**
     * Einen neuen Mitarbeiter in einem Buchungsbetrieb anlegen.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param anfrage   Anfrage mit Mitarbeiterdaten (Name, E-Mail, Rolle, Zeitzone)
     * @return Der erstellte Mitarbeiter
     */
    BookingStaffMemberDto mitarbeiterErstellen(String betriebId, CreateStaffMemberRequest anfrage);

    /**
     * Einen bestehenden Mitarbeiter aktualisieren.
     *
     * @param betriebId     ID des Buchungsbetriebs
     * @param mitarbeiterId ID des zu aktualisierenden Mitarbeiters
     * @param anfrage       Anfrage mit den neuen Mitarbeiterdaten
     * @return Der aktualisierte Mitarbeiter
     */
    BookingStaffMemberDto mitarbeiterAktualisieren(String betriebId,
                                                   String mitarbeiterId,
                                                   CreateStaffMemberRequest anfrage);

    /**
     * Einen Mitarbeiter aus einem Buchungsbetrieb entfernen.
     *
     * @param betriebId     ID des Buchungsbetriebs
     * @param mitarbeiterId ID des zu entfernenden Mitarbeiters
     */
    void mitarbeiterLoeschen(String betriebId, String mitarbeiterId);

    /**
     * Mitarbeiter eines Buchungsbetriebs nach Name oder E-Mail suchen.
     *
     * Die Graph-API unterstützt kein serverseitiges $filter für staffMembers,
     * daher wird die komplette Liste abgerufen und clientseitig gefiltert.
     *
     * Suchkriterien (OR-verknüpft, Groß-/Kleinschreibung wird ignoriert):
     *   - {@code suchbegriff} wird in {@code displayName} und {@code emailAddress} gesucht
     *
     * @param betriebId   ID des Buchungsbetriebs
     * @param suchbegriff Vorname, Nachname oder E-Mail-Adresse (Teilstring möglich)
     * @return Liste der passenden Mitarbeiter (leer, wenn kein Treffer)
     */
    List<BookingStaffMemberDto> mitarbeiterSuchen(String betriebId, String suchbegriff);

    /**
     * Verfügbarkeit (frei / belegt) eines oder mehrerer Mitarbeiter
     * in einem Zeitraum abfragen.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param anfrage   Anfrage mit Mitarbeiter-IDs und Zeitraum
     * @return Liste der Verfügbarkeitselemente pro Mitarbeiter
     */
    List<StaffAvailabilityItemDto> mitarbeiterVerfuegbarkeitAbrufen(String betriebId,
                                                                     StaffAvailabilityRequestDto anfrage);
}
