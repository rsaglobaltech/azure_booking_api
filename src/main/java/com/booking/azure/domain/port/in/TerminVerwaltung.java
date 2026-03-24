package com.booking.azure.domain.port.in;

import com.booking.azure.dto.BookingAppointmentDto;
import com.booking.azure.dto.request.CreateAppointmentRequest;

import java.util.List;

/**
 * Eingehender Port (Use-Case-Interface) für die Terminverwaltung.
 *
 * Onion-Architektur – Domänenschicht:
 *   Dieser Port hat keine Abhängigkeiten von äußeren Schichten.
 *   Die Präsentationsschicht (Controller) ruft ausschließlich dieses
 *   Interface auf; die konkrete Implementierung liegt in der
 *   Anwendungsschicht (ApplicationService).
 *
 * Buchungs-URL-Muster: https://outlook.office.com/book/{agenturName}@domain.com
 */
public interface TerminVerwaltung {

    /**
     * Alle Termine eines Buchungsbetriebs (Agentur) auflisten.
     *
     * @param betriebId ID des Buchungsbetriebs (z. B. agentur@midominio.com)
     * @return Liste aller Termine des Betriebs
     */
    List<BookingAppointmentDto> termineAuflisten(String betriebId);

    /**
     * Termine in einem Datumszeitraum abrufen (Kalenderansicht).
     *
     * @param betriebId       ID des Buchungsbetriebs
     * @param startDatumZeit  Startdatum im ISO-8601-Format (z. B. 2024-06-01T00:00:00Z)
     * @param endDatumZeit    Enddatum im ISO-8601-Format
     * @return Liste der Termine im angegebenen Zeitraum
     */
    List<BookingAppointmentDto> kalenderAnsichtAbrufen(String betriebId,
                                                       String startDatumZeit,
                                                       String endDatumZeit);

    /**
     * Einen bestimmten Termin abrufen.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param terminId  ID (GUID) des Termins
     * @return Termindaten
     */
    BookingAppointmentDto terminAbrufen(String betriebId, String terminId);

    /**
     * Einen neuen Termin erstellen und einem Mitarbeiter zuweisen.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param anfrage   Anfrage mit Dienst-, Zeit- und Kundendaten
     * @return Der erstellte Termin
     */
    BookingAppointmentDto terminErstellen(String betriebId, CreateAppointmentRequest anfrage);

    /**
     * Einen bestehenden Termin aktualisieren.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param terminId  ID des zu aktualisierenden Termins
     * @param anfrage   Anfrage mit den neuen Daten
     * @return Der aktualisierte Termin
     */
    BookingAppointmentDto terminAktualisieren(String betriebId,
                                              String terminId,
                                              CreateAppointmentRequest anfrage);

    /**
     * Einen Termin stornieren / löschen.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param terminId  ID des zu stornierenden Termins
     */
    void terminStornieren(String betriebId, String terminId);
}
