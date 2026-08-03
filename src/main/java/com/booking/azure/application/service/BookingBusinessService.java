package com.booking.azure.application.service;

import com.booking.azure.application.port.in.AgencyManagement;
import com.booking.azure.application.port.out.GraphApiRequest;
import com.booking.azure.dto.BookingBusinessDto;
import com.booking.azure.application.dto.ListResponse;
import com.booking.azure.application.command.CreateBookingBusinessRequest;
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
 *   Implementiert den eingehenden Port {@link AgencyManagement}.
 *   Abhängig vom ausgehenden Port {@link GraphApiRequest}.
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
public class BookingBusinessService implements AgencyManagement {

    /** Ausgehender Port – wird durch den GraphApiClient implementiert */
    private final GraphApiRequest graphApiRequest;
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
    public List<BookingBusinessDto> listBusinesses() {
        log.info("Alle Buchungsbetriebe des Mandanten werden aufgelistet");
        ListResponse<BookingBusinessDto> response = graphApiRequest.get(API_PFAD,
                ListResponse.class);
        List<BookingBusinessDto> betriebe = listeMappen(response.getValue(), BookingBusinessDto.class);
        betriebe.forEach(this::buchungsUrlSetzen);
        return betriebe;
    }

    /**
     * Einen Buchungsbetrieb anhand seiner ID abrufen.
     * Die Buchungs-URL wird dynamisch aus der ID berechnet:
     * {@code https://outlook.office.com/book/{agenturName}@midominio.com}
     *
     * @param businessId ID des Buchungsbetriebs (dynamischer Agenturname)
     * @return Betriebsdaten inkl. Buchungs-URL
     */
    @Override
    public BookingBusinessDto getBusiness(String businessId) {
        log.info("Buchungsbetrieb wird abgerufen: {}", businessId);
        BookingBusinessDto business = graphApiRequest.get(API_PFAD + "/" + businessId,
                BookingBusinessDto.class);
        buchungsUrlSetzen(business);
        return business;
    }

    /**
     * Neuen Buchungsbetrieb (Agentur) erstellen.
     *
     * @param request Anfrage mit Anzeigename und Betriebsdaten
     * @return Der erstellte Betrieb mit berechneter Buchungs-URL
     */
    @Override
    public BookingBusinessDto createBusiness(CreateBookingBusinessRequest request) {
        log.info("Neuer Buchungsbetrieb wird erstellt: {}", request.getDisplayName());
        BookingBusinessDto ergebnis = graphApiRequest.post(API_PFAD, request, BookingBusinessDto.class);
        buchungsUrlSetzen(ergebnis);
        return ergebnis;
    }

    /**
     * Bestehenden Buchungsbetrieb aktualisieren.
     *
     * @param businessId ID des Buchungsbetriebs
     * @param request   Neue Betriebsdaten
     * @return Der aktualisierte Betrieb
     */
    @Override
    public BookingBusinessDto updateBusiness(String businessId,
                                                   CreateBookingBusinessRequest request) {
        log.info("Buchungsbetrieb wird aktualisiert: {}", businessId);
        BookingBusinessDto ergebnis = graphApiRequest.patch(API_PFAD + "/" + businessId,
                request, BookingBusinessDto.class);
        buchungsUrlSetzen(ergebnis);
        return ergebnis;
    }

    /**
     * Buchungsbetrieb dauerhaft löschen.
     *
     * @param businessId ID des zu löschenden Buchungsbetriebs
     */
    @Override
    public void deleteBusiness(String businessId) {
        log.info("Buchungsbetrieb wird gelöscht: {}", businessId);
        graphApiRequest.delete(API_PFAD + "/" + businessId);
    }

    /**
     * Buchungsseite einer Agentur veröffentlichen.
     * Nach Veröffentlichung ist die Seite unter der dynamischen Buchungs-URL erreichbar:
     * {@code https://outlook.office.com/book/{agenturName}@midominio.com}
     *
     * @param businessId ID des Buchungsbetriebs
     */
    @Override
    public void publishBusiness(String businessId) {
        log.info("Buchungsseite wird veröffentlicht für Betrieb: {}", businessId);
        log.info("Buchungs-URL nach Veröffentlichung: {}{}", buchungsBasisUrl, businessId);
        graphApiRequest.post(API_PFAD + "/" + businessId + "/publish", "", String.class);
    }

    /**
     * Buchungsseite einer Agentur deaktivieren.
     *
     * @param businessId ID des Buchungsbetriebs
     */
    @Override
    public void deactivateBusiness(String businessId) {
        log.info("Buchungsseite wird deaktiviert für Betrieb: {}", businessId);
        graphApiRequest.post(API_PFAD + "/" + businessId + "/unpublish", "", String.class);
    }

    // ─────────────────────────────── Hilfsmethoden ──────────────────────────────

    /**
     * Die dynamische Buchungs-URL eines Betriebs berechnen und setzen.
     * Format: {@code https://outlook.office.com/book/{agenturName}@midominio.com}
     * Der Agenturname (businessId) ist immer dynamisch.
     *
     * @param business Buchungsbetrieb-DTO
     */
    private void buchungsUrlSetzen(BookingBusinessDto business) {
        if (business != null && business.getId() != null && !business.getId().isBlank()) {
            business.setBuchungsUrl(buchungsBasisUrl + business.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> listeMappen(List<?> rohliste, Class<T> zielklasse) {
        if (rohliste == null) return List.of();
        return objectMapper.convertValue(rohliste,
                objectMapper.getTypeFactory().constructCollectionType(List.class, zielklasse));
    }
}


