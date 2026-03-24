package com.booking.azure.service;

import com.booking.azure.domain.port.in.TerminVerwaltung;
import com.booking.azure.domain.port.out.GraphApiAnfrage;
import com.booking.azure.dto.BookingAppointmentDto;
import com.booking.azure.dto.BookingCustomerInfoDto;
import com.booking.azure.dto.GraphListResponse;
import com.booking.azure.dto.LocationDto;
import com.booking.azure.dto.PhysicalAddressDto;
import com.booking.azure.dto.request.CreateAppointmentRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * Mapping:
 *   Die vereinfachte {@link CreateAppointmentRequest} (Kundenfelder, location,
 *   notificationsEnabled) wird intern auf den vollständigen Graph-API-Payload
 *   ({@link GraphTerminPayload}) gemappt, bevor er an die Graph-API gesendet wird.
 *   StaffMemberIds werden automatisch aus der Konfiguration befüllt.
 */
@Slf4j
@Service
public class AppointmentService implements TerminVerwaltung {

    /** Ausgehender Port – wird durch den GraphApiClient implementiert */
    private final GraphApiAnfrage graphApiAnfrage;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Konfigurierbare Standard-Mitarbeiter-IDs (GUIDs), die automatisch
     * jedem Termin zugewiesen werden.
     * Wird über {@code buchung.default-staff-ids} in application.yml gesetzt.
     * Leer = Graph API weist Mitarbeiter automatisch zu.
     */
    @Value("${buchung.default-staff-ids:}")
    private List<String> defaultStaffIds;

    public AppointmentService(GraphApiAnfrage graphApiAnfrage,
                              com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.graphApiAnfrage = graphApiAnfrage;
        this.objectMapper = objectMapper;
    }

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
     * Neuen Termin erstellen.
     * Die vereinfachte {@link CreateAppointmentRequest} wird auf den vollständigen
     * Graph-API-Payload gemappt. StaffMemberIds werden automatisch befüllt.
     *
     * @param betriebId ID der Agentur
     * @param anfrage   Vereinfachte Termindaten vom Client
     * @return Der erstellte Termin
     */
    @Override
    public BookingAppointmentDto terminErstellen(String betriebId, CreateAppointmentRequest anfrage) {
        log.info("Neuer Termin wird erstellt in Betrieb {}, Dienst: {}, Kunde: {} {}",
                betriebId, anfrage.getServiceId(),
                anfrage.getKunde().getVorname(), anfrage.getKunde().getNachname());
        GraphTerminPayload payload = anfrageZuGraphPayload(anfrage);
        return graphApiAnfrage.post(terminePfad(betriebId), payload, BookingAppointmentDto.class);
    }

    /**
     * Bestehenden Termin aktualisieren.
     *
     * @param betriebId ID der Agentur
     * @param terminId  ID des Termins
     * @param anfrage   Neue vereinfachte Termindaten
     * @return Der aktualisierte Termin
     */
    @Override
    public BookingAppointmentDto terminAktualisieren(String betriebId,
                                                     String terminId,
                                                     CreateAppointmentRequest anfrage) {
        log.info("Termin {} wird aktualisiert in Betrieb {}", terminId, betriebId);
        GraphTerminPayload payload = anfrageZuGraphPayload(anfrage);
        return graphApiAnfrage.patch(terminePfad(betriebId) + "/" + terminId,
                payload, BookingAppointmentDto.class);
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

    // ─────────────────────────────── Mapping ──────────────────────────────────

    /**
     * Mappt die vereinfachte {@link CreateAppointmentRequest} auf den
     * vollständigen {@link GraphTerminPayload}, der an die Graph-API gesendet wird.
     *
     * Mapping-Regeln:
     * - vorname + nachname                 → customerName + customers[0].name
     * - email                              → customerEmailAddress + customers[0].emailAddress
     * - telefon (optional)                 → customerPhone + customers[0].phone
     * - anmerkungen (optional)             → customers[0].notes
     * - location (strasse/ort/plz/land)    → serviceLocation + customers[0].location
     * - notificationsEnabled               → optOutOfCustomerEmail (!), smsNotificationsEnabled
     * - defaultStaffIds (config)           → staffMemberIds (automatisch)
     */
    private GraphTerminPayload anfrageZuGraphPayload(CreateAppointmentRequest anfrage) {
        CreateAppointmentRequest.KundeRequest k = anfrage.getKunde();
        CreateAppointmentRequest.AdresseRequest a = anfrage.getLocation();

        String vollstaendigerName = k.getVorname() + " " + k.getNachname();
        String displayNameAdresse = a.getStrasse() + ", " + a.getPlz() + " " + a.getOrt()
                + ", " + (a.getLand() != null ? a.getLand() : "Deutschland");

        // Adresse aufbauen
        PhysicalAddressDto physAdresse = new PhysicalAddressDto();
        physAdresse.setStreet(a.getStrasse());
        physAdresse.setCity(a.getOrt());
        physAdresse.setPostalCode(a.getPlz());
        physAdresse.setCountryOrRegion(a.getLand() != null ? a.getLand() : "Deutschland");

        // Dienstort (serviceLocation)
        LocationDto serviceLocation = new LocationDto();
        serviceLocation.setDisplayName(displayNameAdresse);
        serviceLocation.setAddress(physAdresse);

        // Kundenstandort für das customers-Array
        LocationDto kundenOrt = new LocationDto();
        kundenOrt.setDisplayName("Kundenadresse");
        kundenOrt.setAddress(physAdresse);

        // customers[0] aufbauen
        BookingCustomerInfoDto kundeInfo = new BookingCustomerInfoDto();
        kundeInfo.setName(vollstaendigerName);
        kundeInfo.setEmailAddress(k.getEmail());
        kundeInfo.setPhone(k.getTelefon());
        kundeInfo.setNotes(k.getAnmerkungen());
        kundeInfo.setLocation(kundenOrt);

        // Vollständigen Payload aufbauen
        GraphTerminPayload payload = new GraphTerminPayload();
        payload.setServiceId(anfrage.getServiceId());
        payload.setStart(anfrage.getStart());
        payload.setEnd(anfrage.getEnd());
        payload.setServiceLocation(serviceLocation);
        payload.setCustomerName(vollstaendigerName);
        payload.setCustomerEmailAddress(k.getEmail());
        payload.setCustomerPhone(k.getTelefon());
        payload.setOptOutOfCustomerEmail(!anfrage.isNotificationsEnabled());
        payload.setSmsNotificationsEnabled(anfrage.isNotificationsEnabled());
        // staffMemberIds: explizite ID aus Request hat Vorrang vor Konfiguration
        List<String> staffIds;
        if (anfrage.getMitarbeiterId() != null && !anfrage.getMitarbeiterId().isBlank()) {
            staffIds = List.of(anfrage.getMitarbeiterId());
            log.debug("Mitarbeiter aus Request: {}", anfrage.getMitarbeiterId());
        } else {
            staffIds = defaultStaffIds;
            log.debug("Mitarbeiter aus Konfiguration: {}", defaultStaffIds);
        }
        payload.setStaffMemberIds(staffIds);
        payload.setCustomers(List.of(kundeInfo));

        return payload;
    }

    // ─────────────────────────── Graph-API-Payload ─────────────────────────────

    /**
     * Interner Payload, der exakt der Microsoft-Graph-API-Struktur entspricht.
     * Wird nie direkt an den API-Client zurückgegeben.
     */
    @Data
    private static class GraphTerminPayload {

        @JsonProperty("serviceId")
        private String serviceId;

        @JsonProperty("start")
        private com.booking.azure.dto.DateTimeTimeZoneDto start;

        @JsonProperty("end")
        private com.booking.azure.dto.DateTimeTimeZoneDto end;

        @JsonProperty("serviceLocation")
        private LocationDto serviceLocation;

        @JsonProperty("customerName")
        private String customerName;

        @JsonProperty("customerEmailAddress")
        private String customerEmailAddress;

        @JsonProperty("customerPhone")
        private String customerPhone;

        /** true = kein E-Mail-Versand an den Kunden */
        @JsonProperty("optOutOfCustomerEmail")
        private Boolean optOutOfCustomerEmail;

        @JsonProperty("smsNotificationsEnabled")
        private Boolean smsNotificationsEnabled;

        /** Automatisch aus Konfiguration befüllt (buchung.default-staff-ids) */
        @JsonProperty("staffMemberIds")
        private List<String> staffMemberIds;

        @JsonProperty("customers")
        private List<BookingCustomerInfoDto> customers;
    }

    // ─────────────────────────────── Hilfsmethoden ──────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> List<T> listeMappen(List<?> rohliste, Class<T> zielklasse) {
        if (rohliste == null) return List.of();
        return objectMapper.convertValue(rohliste,
                objectMapper.getTypeFactory().constructCollectionType(List.class, zielklasse));
    }
}
