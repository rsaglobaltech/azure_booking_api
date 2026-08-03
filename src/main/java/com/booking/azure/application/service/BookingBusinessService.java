package com.booking.azure.application.service;

import com.booking.azure.application.port.in.AgencyManagement;
import com.booking.azure.domain.model.Agency;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.TenantId;
import com.booking.azure.domain.port.out.AgencyRepository;
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
    private final AgencyRepository agencyRepository;

    /** The platform's own Entra ID directory. */
    @Value("${azure.graph.tenant-id}")
    private String homeTenantId;

    private final ObjectMapper objectMapper;

    /** Basis-URL der Microsoft Bookings Selbstbuchungsseite */
    @Value("${buchung.buchungs-basis-url:https://outlook.office.com/book/}")
    private String buchungsBasisUrl;

    private static final String API_PFAD = "/solutions/bookingBusinesses";

    // ──────────────────────────── Use-Case-Implementierungen ────────────────────

    /**
     * Lists every agency this platform serves.
     *
     * <h2>Why the local database is the source of the list</h2>
     *
     * Agencies live in <b>different</b> Entra ID directories. No single call to
     * Microsoft can enumerate them: a token is scoped to one tenant, so asking
     * Graph for "all booking businesses" returns the contents of whichever
     * directory that token belongs to — the platform's own, which holds none of
     * the customers' agencies. The registration table is the only place that
     * knows the full set.
     *
     * <h2>Why Graph is still called, once per agency</h2>
     *
     * The registration row holds identifiers, not business details: address,
     * opening hours, scheduling policy and publication state live in Bookings.
     * Each agency is therefore fetched from its own directory, with its own
     * token, so the response keeps the shape callers already parse.
     *
     * <p>An agency whose directory cannot be reached is <b>still listed</b>,
     * with the identifiers and booking URL known locally. Dropping it would
     * make an unreachable agency indistinguishable from a deregistered one, and
     * one unavailable directory would silently shrink everyone else's list.
     */
    @Override
    public List<BookingBusinessDto> listBusinesses() {
        List<Agency> agencies = agencyRepository.findAll();
        log.info("Listing {} registered agencies", agencies.size());

        return agencies.stream().map(this::describe).toList();
    }

    /** Fetches one agency's details from its own directory, or falls back to what is known locally. */
    private BookingBusinessDto describe(Agency agency) {
        try {
            BookingBusinessDto business = graphApiRequest.get(
                    agency.tenantId(),
                    API_PFAD + "/" + agency.businessId().value(),
                    BookingBusinessDto.class);
            buchungsUrlSetzen(business);
            return business;

        } catch (RuntimeException ex) {
            log.warn("Agency {} could not be read from tenant {}, listing it with local data only: {}",
                    agency.businessId(), agency.tenantId(), ex.getMessage());
            return localOnly(agency);
        }
    }

    private BookingBusinessDto localOnly(Agency agency) {
        BookingBusinessDto business = new BookingBusinessDto();
        business.setId(agency.businessId().value());
        business.setDisplayName(agency.name().value());
        buchungsUrlSetzen(business);
        return business;
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
        BookingBusinessDto business = graphApiRequest.get(tenantOf(businessId), API_PFAD + "/" + businessId,
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
        BookingBusinessDto ergebnis = graphApiRequest.post(homeTenant(), API_PFAD, request, BookingBusinessDto.class);
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
        BookingBusinessDto ergebnis = graphApiRequest.patch(tenantOf(businessId), API_PFAD + "/" + businessId,
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
        graphApiRequest.delete(tenantOf(businessId), API_PFAD + "/" + businessId);
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
        graphApiRequest.post(tenantOf(businessId), API_PFAD + "/" + businessId + "/publish", "", String.class);
    }

    /**
     * Buchungsseite einer Agentur deaktivieren.
     *
     * @param businessId ID des Buchungsbetriebs
     */
    @Override
    public void deactivateBusiness(String businessId) {
        log.info("Buchungsseite wird deaktiviert für Betrieb: {}", businessId);
        graphApiRequest.post(tenantOf(businessId), API_PFAD + "/" + businessId + "/unpublish", "", String.class);
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

    // ─────────────────────────── tenant resolution ───────────────────────────

    /**
     * The directory a booking business lives in.
     *
     * Falls back to the platform's own tenant when the business is not
     * registered locally — the administrative surface can also manage Graph
     * objects that have no mapping row yet, for instance while one is being
     * created.
     */
    private TenantId tenantOf(String businessId) {
        return agencyRepository.findByBusinessId(BusinessId.of(businessId))
                .map(Agency::tenantId)
                .orElseGet(this::homeTenant);
    }

    /** The platform's own directory, for calls that name no agency. */
    private TenantId homeTenant() {
        return TenantId.of(homeTenantId);
    }
}
