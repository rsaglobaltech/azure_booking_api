package com.booking.azure.service;

import com.booking.azure.domain.port.in.DienstVerwaltung;
import com.booking.azure.domain.port.out.GraphApiAnfrage;
import com.booking.azure.dto.BookingServiceDto;
import com.booking.azure.dto.GraphListResponse;
import com.booking.azure.dto.request.CreateServiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anwendungsdienst für die Verwaltung von Buchungsdienstleistungen.
 *
 * Onion-Architektur – Anwendungsschicht:
 *   Implementiert den eingehenden Port {@link DienstVerwaltung}.
 *   Abhängig vom ausgehenden Port {@link GraphApiAnfrage}.
 *
 * Jeder Dienst gehört zu einem Buchungsbetrieb (Agentur) und
 * kann von Kunden über die öffentliche Buchungs-URL gebucht werden:
 *   https://outlook.office.com/book/{agenturName}@midominio.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceMgr implements DienstVerwaltung {

    /** Ausgehender Port – wird durch den GraphApiClient implementiert */
    private final GraphApiAnfrage graphApiAnfrage;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String dienstePfad(String betriebId) {
        return "/solutions/bookingBusinesses/" + betriebId + "/services";
    }

    /**
     * Alle Dienstleistungen eines Buchungsbetriebs auflisten.
     *
     * @param betriebId ID der Agentur
     * @return Liste aller Dienste
     */
    @Override
    public List<BookingServiceDto> diensteAuflisten(String betriebId) {
        log.info("Dienste werden aufgelistet für Betrieb: {}", betriebId);
        GraphListResponse<BookingServiceDto> antwort = graphApiAnfrage.get(
                dienstePfad(betriebId), GraphListResponse.class);
        return listeMappen(antwort.getValue(), BookingServiceDto.class);
    }

    /**
     * Einen bestimmten Dienst abrufen.
     *
     * @param betriebId ID der Agentur
     * @param dienstId  ID (GUID) des Dienstes
     * @return Dienstdaten
     */
    @Override
    public BookingServiceDto dienstAbrufen(String betriebId, String dienstId) {
        log.info("Dienst {} wird abgerufen für Betrieb {}", dienstId, betriebId);
        return graphApiAnfrage.get(dienstePfad(betriebId) + "/" + dienstId, BookingServiceDto.class);
    }

    /**
     * Neuen Dienst in einem Buchungsbetrieb erstellen.
     *
     * @param betriebId ID der Agentur
     * @param anfrage   Dienstdaten (Name, Dauer, Preis, zugewiesene Mitarbeiter)
     * @return Der erstellte Dienst
     */
    @Override
    public BookingServiceDto dienstErstellen(String betriebId, CreateServiceRequest anfrage) {
        log.info("Neuer Dienst '{}' wird erstellt in Betrieb {}", anfrage.getDisplayName(), betriebId);
        return graphApiAnfrage.post(dienstePfad(betriebId), anfrage, BookingServiceDto.class);
    }

    /**
     * Bestehenden Dienst aktualisieren.
     *
     * @param betriebId ID der Agentur
     * @param dienstId  ID des Dienstes
     * @param anfrage   Neue Dienstdaten
     * @return Der aktualisierte Dienst
     */
    @Override
    public BookingServiceDto dienstAktualisieren(String betriebId,
                                                 String dienstId,
                                                 CreateServiceRequest anfrage) {
        log.info("Dienst {} wird aktualisiert in Betrieb {}", dienstId, betriebId);
        return graphApiAnfrage.patch(dienstePfad(betriebId) + "/" + dienstId,
                anfrage, BookingServiceDto.class);
    }

    /**
     * Dienst aus einem Buchungsbetrieb löschen.
     *
     * @param betriebId ID der Agentur
     * @param dienstId  ID des zu löschenden Dienstes
     */
    @Override
    public void dienstLoeschen(String betriebId, String dienstId) {
        log.info("Dienst {} wird gelöscht in Betrieb {}", dienstId, betriebId);
        graphApiAnfrage.delete(dienstePfad(betriebId) + "/" + dienstId);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> listeMappen(List<?> rohliste, Class<T> zielklasse) {
        if (rohliste == null) return List.of();
        return objectMapper.convertValue(rohliste,
                objectMapper.getTypeFactory().constructCollectionType(List.class, zielklasse));
    }
}
