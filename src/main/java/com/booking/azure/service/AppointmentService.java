package com.booking.azure.service;

import com.booking.azure.domain.port.in.TerminVerwaltung;
import com.booking.azure.domain.port.out.GraphApiAnfrage;
import com.booking.azure.dto.BookingAppointmentDto;
import com.booking.azure.dto.GraphListResponse;
import com.booking.azure.dto.request.CreateAppointmentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anwendungsdienst für die Terminverwaltung (Appointments).
 *
 * Onion-Architektur – Anwendungsschicht:
 *   Implementiert den eingehenden Port {@link TerminVerwaltung}.
 *   Abhängig vom ausgehenden Port {@link GraphApiAnfrage}; die konkrete
 *   Infrastruktur (GraphApiClient) ist dieser Schicht unbekannt.
 *
 * Alle Terme beziehen sich auf einen einzelnen Buchungsbetrieb (Agentur)
 * innerhalb desselben Azure-AD-Mandanten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService implements TerminVerwaltung {

    /** Ausgehender Port – wird durch den GraphApiClient implementiert */
    private final GraphApiAnfrage graphApiAnfrage;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ──────────────────────────────── Hilfsmethoden ────────────────────────────

    private String terminePfad(String betriebId) {
        return "/solutions/bookingBusinesses/" + betriebId + "/appointments";
    }

    private String kalenderPfad(String betriebId) {
        return "/solutions/bookingBusinesses/" + betriebId + "/calendarView";
    }

    // ──────────────────────────── Use-Case-Implementierungen ────────────────────

    /**
     * Alle Termine eines Buchungsbetriebs auflisten.
     *
     * @param betriebId ID der Agentur (z. B. agentur@midominio.com)
     * @return Liste aller Termine
     */
    @Override
    public List<BookingAppointmentDto> termineAuflisten(String betriebId) {
        log.info("Termine werden aufgelistet für Betrieb: {}", betriebId);
        GraphListResponse<BookingAppointmentDto> antwort = graphApiAnfrage.get(
                terminePfad(betriebId), GraphListResponse.class);
        return listeMappen(antwort.getValue(), BookingAppointmentDto.class);
    }

    /**
     * Termine in einem Datumszeitraum abrufen (Kalenderansicht).
     * Datumsformat ISO 8601, z. B. {@code 2024-01-01T00:00:00Z}
     *
     * @param betriebId      ID der Agentur
     * @param startDatumZeit Startdatum und -zeit
     * @param endDatumZeit   Enddatum und -zeit
     * @return Liste der Termine im Zeitraum
     */
    @Override
    public List<BookingAppointmentDto> kalenderAnsichtAbrufen(String betriebId,
                                                              String startDatumZeit,
                                                              String endDatumZeit) {
        log.info("Kalenderansicht für Betrieb {}: {} → {}", betriebId, startDatumZeit, endDatumZeit);
        String pfad = kalenderPfad(betriebId)
                + "?startDateTime=" + startDatumZeit
                + "&endDateTime=" + endDatumZeit;
        GraphListResponse<BookingAppointmentDto> antwort = graphApiAnfrage.get(pfad, GraphListResponse.class);
        return listeMappen(antwort.getValue(), BookingAppointmentDto.class);
    }

    /**
     * Einen einzelnen Termin anhand seiner ID abrufen.
     *
     * @param betriebId ID der Agentur
     * @param terminId  ID (GUID) des Termins
     * @return Termindaten
     */
    @Override
    public BookingAppointmentDto terminAbrufen(String betriebId, String terminId) {
        log.info("Termin {} wird abgerufen für Betrieb {}", terminId, betriebId);
        return graphApiAnfrage.get(terminePfad(betriebId) + "/" + terminId,
                BookingAppointmentDto.class);
    }

    /**
     * Neuen Termin erstellen und Mitarbeiter sowie Kunden zuweisen.
     *
     * @param betriebId ID der Agentur
     * @param anfrage   Termindaten (Dienst, Zeitraum, Mitarbeiter, Kunde)
     * @return Der erstellte Termin
     */
    @Override
    public BookingAppointmentDto terminErstellen(String betriebId, CreateAppointmentRequest anfrage) {
        log.info("Neuer Termin wird erstellt in Betrieb {}, Dienst: {}", betriebId, anfrage.getServiceId());
        return graphApiAnfrage.post(terminePfad(betriebId), anfrage, BookingAppointmentDto.class);
    }

    /**
     * Bestehenden Termin aktualisieren.
     *
     * @param betriebId ID der Agentur
     * @param terminId  ID des Termins
     * @param anfrage   Neue Termindaten
     * @return Der aktualisierte Termin
     */
    @Override
    public BookingAppointmentDto terminAktualisieren(String betriebId,
                                                     String terminId,
                                                     CreateAppointmentRequest anfrage) {
        log.info("Termin {} wird aktualisiert in Betrieb {}", terminId, betriebId);
        return graphApiAnfrage.patch(terminePfad(betriebId) + "/" + terminId,
                anfrage, BookingAppointmentDto.class);
    }

    /**
     * Termin stornieren / löschen.
     *
     * @param betriebId ID der Agentur
     * @param terminId  ID des zu stornierenden Termins
     */
    @Override
    public void terminStornieren(String betriebId, String terminId) {
        log.info("Termin {} wird storniert in Betrieb {}", terminId, betriebId);
        graphApiAnfrage.delete(terminePfad(betriebId) + "/" + terminId);
    }

    // ─────────────────────────────── Hilfsmethoden ──────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> List<T> listeMappen(List<?> rohliste, Class<T> zielklasse) {
        if (rohliste == null) return List.of();
        return objectMapper.convertValue(rohliste,
                objectMapper.getTypeFactory().constructCollectionType(List.class, zielklasse));
    }
}
