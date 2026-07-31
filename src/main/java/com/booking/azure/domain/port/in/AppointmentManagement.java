package com.booking.azure.domain.port.in;

import com.booking.azure.dto.BookingAppointmentDto;
import com.booking.azure.domain.command.CreateAppointmentRequest;

import java.util.List;

/**
 * Eingehender Port (Use-Case-Interface) für die Terminverwaltung.
 *
 * Onion-Architektur – Domänenschicht:
 * Dieser Port hat keine Abhängigkeiten von äußeren Schichten.
 * Die Präsentationsschicht (Controller) ruft ausschließlich dieses
 * Interface auf; die konkrete Implementierung liegt in der
 * Anwendungsschicht (ApplicationService).
 *
 * Buchungs-URL-Muster: https://outlook.office.com/book/{agenturName}@domain.com
 */
public interface AppointmentManagement {

    /**
     * Alle Termine eines Buchungsbetriebs (Agentur) auflisten.
     *
     * @param agencyName ID des Buchungsbetriebs (z. B. agency@midominio.com)
     * @return Liste aller Termine des Betriebs
     */
    List<BookingAppointmentDto> listAppointments(String agencyName);

    /**
     * Termine in einem Datumszeitraum abrufen (Kalenderansicht).
     *
     * @param agencyName     ID des Buchungsbetriebs
     * @param startDatumZeit Startdatum im ISO-8601-Format (z. B.
     *                       2024-06-01T00:00:00Z)
     * @param endDatumZeit   Enddatum im ISO-8601-Format
     * @return Liste der Termine im angegebenen Zeitraum
     */
    List<BookingAppointmentDto> kalenderAnsichtAbrufen(String agencyName,
            String startDatumZeit,
            String endDatumZeit);

    /**
     * Einen bestimmten Termin abrufen.
     *
     * @param agencyName    ID des Buchungsbetriebs
     * @param appointmentId ID (GUID) des Termins
     * @return Termindaten
     */
    BookingAppointmentDto getAppointment(String agencyName, String appointmentId);

    /**
     * Einen neuen Termin erstellen und einem Mitarbeiter zuweisen.
     *
     * @param agencyName ID des Buchungsbetriebs
     * @param request    Anfrage mit Dienst-, Zeit- und Kundendaten
     * @return Der erstellte Termin
     */
    BookingAppointmentDto createAppointment(String agencyName, CreateAppointmentRequest request);

    /**
     * Einen bestehenden Termin aktualisieren.
     *
     * @param agencyName    ID des Buchungsbetriebs
     * @param appointmentId ID des zu aktualisierenden Termins
     * @param request       Anfrage mit den neuen Daten
     * @return Der aktualisierte Termin
     */
    BookingAppointmentDto updateAppointment(String agencyName,
            String appointmentId,
            CreateAppointmentRequest request);

    /**
     * Einen Termin stornieren / löschen.
     *
     * @param agencyName    ID des Buchungsbetriebs
     * @param appointmentId ID des zu stornierenden Termins
     */
    void cancelAppointment(String agencyName, String appointmentId);
}
