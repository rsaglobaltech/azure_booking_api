package com.booking.azure.application.port.in;

import com.booking.azure.dto.BookingStaffMemberDto;
import com.booking.azure.dto.StaffAvailabilityItemDto;
import com.booking.azure.dto.StaffAvailabilityRequestDto;
import com.booking.azure.application.command.CreateStaffMemberRequest;

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
public interface StaffManagement {

    /**
     * Alle Mitarbeiter eines Buchungsbetriebs auflisten.
     *
     * @param businessId ID des Buchungsbetriebs
     * @return Liste aller Mitarbeiter
     */
    List<BookingStaffMemberDto> listStaffMembers(String businessId);

    /**
     * Einen bestimmten Mitarbeiter abrufen.
     *
     * @param businessId    ID des Buchungsbetriebs
     * @param mitarbeiterId ID (GUID) des Mitarbeiters
     * @return Mitarbeiterdaten
     */
    BookingStaffMemberDto getStaffMember(String businessId, String mitarbeiterId);

    /**
     * Einen neuen Mitarbeiter in einem Buchungsbetrieb anlegen.
     *
     * @param businessId ID des Buchungsbetriebs
     * @param request   Anfrage mit Mitarbeiterdaten (Name, E-Mail, Rolle, Zeitzone)
     * @return Der erstellte Mitarbeiter
     */
    BookingStaffMemberDto createStaffMember(String businessId, CreateStaffMemberRequest request);

    /**
     * Einen bestehenden Mitarbeiter aktualisieren.
     *
     * @param businessId     ID des Buchungsbetriebs
     * @param mitarbeiterId ID des zu aktualisierenden Mitarbeiters
     * @param request       Anfrage mit den neuen Mitarbeiterdaten
     * @return Der aktualisierte Mitarbeiter
     */
    BookingStaffMemberDto updateStaffMember(String businessId,
                                                   String mitarbeiterId,
                                                   CreateStaffMemberRequest request);

    /**
     * Einen Mitarbeiter aus einem Buchungsbetrieb entfernen.
     *
     * @param businessId     ID des Buchungsbetriebs
     * @param mitarbeiterId ID des zu entfernenden Mitarbeiters
     */
    void deleteStaffMember(String businessId, String mitarbeiterId);

    /**
     * Verfügbarkeit (frei / belegt) eines oder mehrerer Mitarbeiter
     * in einem Zeitraum abfragen.
     *
     * @param businessId ID des Buchungsbetriebs
     * @param request   Anfrage mit Mitarbeiter-IDs und Zeitraum
     * @return Liste der Verfügbarkeitselemente pro Mitarbeiter
     */
    List<StaffAvailabilityItemDto> getStaffMemberAvailability(String businessId,
                                                                     StaffAvailabilityRequestDto request);
}


