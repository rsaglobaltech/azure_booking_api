package com.booking.azure.service;

import com.booking.azure.domain.port.in.AgenturVerwaltung;
import com.booking.azure.domain.port.out.GraphApiAnfrage;
import com.booking.azure.dto.BookingBusinessDto;
import com.booking.azure.dto.GraphListResponse;
import com.booking.azure.dto.request.CreateBookingBusinessRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anwendungsdienst für die Agenturverwaltung (Buchungsbetriebe).
 *
 * Onion-Architektur – Anwendungsschicht:
 *   Implementiert den eingehenden Port {@link AgenturVerwaltung}.
 *   Abhängig vom ausgehenden Port {@link GraphApiAnfrage}.
 *
 * Jede Agentur ist ein eigenständiger {@code BookingBusiness}-Eintrag im
 * selben Azure-AD-Mandanten. Die öffentliche Buchungs-URL wird dynamisch
 * aus der Betriebs-ID berechnet:
 *
 *   <pre>https://outlook.office.com/book/{agenturName}@midominio.com</pre>
 *
 * Der Agenturname ist dabei immer dynamisch (wird nicht hartcodiert).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingBusinessService implements AgenturVerwaltung {

    /** Ausgehender Port – wird durch den GraphApiClient implementiert */
    private final GraphApiAnfrage graphApiAnfrage;
    private final ObjectMapper objectMapper;

    /** Basis-URL der Microsoft Bookings Selbstbuchungsseite */
    @Value("${buchung.buchungs-basis-url:https://outlook.office.com/book/}")
    private String buchungsBasisUrl;

    private static final String API_PFAD = "/solutions/bookingBusinesses";

    // ──────────────────────────── Use-Case-Implementierungen ────────────────────

    /**
     * Alle Buchungsbetriebe (Agenturen) des Mandanten auflisten.
     * Die Buchungs-URL jedes Betriebs wird dynamisch berechnet.
     *
     * @return Liste aller Buchungsbetriebe inkl. Buchungs-URL
     */
    @Override
    public List<BookingBusinessDto> betriebeAuflisten() {
        log.info("Alle Buchungsbetriebe des Mandanten werden aufgelistet");
        GraphListResponse<BookingBusinessDto> antwort = graphApiAnfrage.get(API_PFAD,
                GraphListResponse.class);
        List<BookingBusinessDto> betriebe = listeMappen(antwort.getValue(), BookingBusinessDto.class);
        betriebe.forEach(this::buchungsUrlSetzen);
        return betriebe;
    }

    /**
     * Einen Buchungsbetrieb anhand seiner ID abrufen.
     * Die Buchungs-URL wird dynamisch aus der ID berechnet:
     * {@code https://outlook.office.com/book/{agenturName}@midominio.com}
     *
     * @param betriebId ID des Buchungsbetriebs (dynamischer Agenturname)
     * @return Betriebsdaten inkl. Buchungs-URL
     */
    @Override
    public BookingBusinessDto betriebAbrufen(String betriebId) {
        log.info("Buchungsbetrieb wird abgerufen: {}", betriebId);
        BookingBusinessDto betrieb = graphApiAnfrage.get(API_PFAD + "/" + betriebId,
                BookingBusinessDto.class);
        buchungsUrlSetzen(betrieb);
        return betrieb;
    }

    /**
     * Neuen Buchungsbetrieb (Agentur) erstellen.
     *
     * @param anfrage Anfrage mit Anzeigename und Betriebsdaten
     * @return Der erstellte Betrieb mit berechneter Buchungs-URL
     */
    @Override
    public BookingBusinessDto betriebErstellen(CreateBookingBusinessRequest anfrage) {
        log.info("Neuer Buchungsbetrieb wird erstellt: {}", anfrage.getDisplayName());
        BookingBusinessDto ergebnis = graphApiAnfrage.post(API_PFAD, anfrage, BookingBusinessDto.class);
        buchungsUrlSetzen(ergebnis);
        return ergebnis;
    }

    /**
     * Bestehenden Buchungsbetrieb aktualisieren.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param anfrage   Neue Betriebsdaten
     * @return Der aktualisierte Betrieb
     */
    @Override
    public BookingBusinessDto betriebAktualisieren(String betriebId,
                                                   CreateBookingBusinessRequest anfrage) {
        log.info("Buchungsbetrieb wird aktualisiert: {}", betriebId);
        BookingBusinessDto ergebnis = graphApiAnfrage.patch(API_PFAD + "/" + betriebId,
                anfrage, BookingBusinessDto.class);
        buchungsUrlSetzen(ergebnis);
        return ergebnis;
    }

    /**
     * Buchungsbetrieb dauerhaft löschen.
     *
     * @param betriebId ID des zu löschenden Buchungsbetriebs
     */
    @Override
    public void betriebLoeschen(String betriebId) {
        log.info("Buchungsbetrieb wird gelöscht: {}", betriebId);
        graphApiAnfrage.delete(API_PFAD + "/" + betriebId);
    }

    /**
     * Buchungsseite einer Agentur veröffentlichen.
     * Nach Veröffentlichung ist die Seite unter der dynamischen Buchungs-URL erreichbar:
     * {@code https://outlook.office.com/book/{agenturName}@midominio.com}
     *
     * @param betriebId ID des Buchungsbetriebs
     */
    @Override
    public void betriebVeroeffentlichen(String betriebId) {
        log.info("Buchungsseite wird veröffentlicht für Betrieb: {}", betriebId);
        log.info("Buchungs-URL nach Veröffentlichung: {}{}", buchungsBasisUrl, betriebId);
        graphApiAnfrage.post(API_PFAD + "/" + betriebId + "/publish", "", String.class);
    }

    /**
     * Buchungsseite einer Agentur deaktivieren.
     *
     * @param betriebId ID des Buchungsbetriebs
     */
    @Override
    public void betriebDeaktivieren(String betriebId) {
        log.info("Buchungsseite wird deaktiviert für Betrieb: {}", betriebId);
        graphApiAnfrage.post(API_PFAD + "/" + betriebId + "/unpublish", "", String.class);
    }

    // ─────────────────────────────── Hilfsmethoden ──────────────────────────────

    /**
     * Die dynamische Buchungs-URL eines Betriebs berechnen und setzen.
     * Format: {@code https://outlook.office.com/book/{agenturName}@midominio.com}
     * Der Agenturname (betriebId) ist immer dynamisch.
     *
     * @param betrieb Buchungsbetrieb-DTO
     */
    private void buchungsUrlSetzen(BookingBusinessDto betrieb) {
        if (betrieb != null && betrieb.getId() != null && !betrieb.getId().isBlank()) {
            betrieb.setBuchungsUrl(buchungsBasisUrl + betrieb.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> listeMappen(List<?> rohliste, Class<T> zielklasse) {
        if (rohliste == null) return List.of();
        return objectMapper.convertValue(rohliste,
                objectMapper.getTypeFactory().constructCollectionType(List.class, zielklasse));
    }
}
