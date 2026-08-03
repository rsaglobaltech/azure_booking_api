package com.booking.azure.application.service;

import com.booking.azure.application.port.in.StaffManagement;
import com.booking.azure.domain.model.Agency;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.TenantId;
import com.booking.azure.domain.port.out.AgencyRepository;
import com.booking.azure.application.port.out.GraphApiRequest;
import org.springframework.beans.factory.annotation.Value;
import com.booking.azure.dto.*;
import com.booking.azure.application.command.CreateStaffMemberRequest;
import com.booking.azure.application.dto.ListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anwendungsdienst für die Mitarbeiterverwaltung eines Buchungsbetriebs.
 *
 * Onion-Architektur – Anwendungsschicht:
 *   Implementiert den eingehenden Port {@link StaffManagement}.
 *   Abhängig vom ausgehenden Port {@link GraphApiRequest}.
 *
 * Mitarbeiter können Termine annehmen. Ihre Verfügbarkeit wird über
 * die Microsoft-Graph-API verwaltet. Mögliche Verfügbarkeitsstatus:
 *   - {@code Available}      → Mitarbeiter ist verfügbar
 *   - {@code Busy}           → Mitarbeiter hat einen laufenden Termin
 *   - {@code SlotsAvailable} → Freie Zeitfenster vorhanden
 *   - {@code OutOfOffice}    → Mitarbeiter ist außer Haus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffMemberService implements StaffManagement {

    /** Ausgehender Port – wird durch den GraphApiClient implementiert */
    private final GraphApiRequest graphApiRequest;
    private final AgencyRepository agencyRepository;

    /** The platform's own Entra ID directory. */
    @Value("${azure.graph.tenant-id}")
    private String homeTenantId;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String staffPath(String businessId) {
        return "/solutions/bookingBusinesses/" + businessId + "/staffMembers";
    }

    /**
     * Alle Mitarbeiter eines Buchungsbetriebs auflisten.
     *
     * @param businessId ID der Agentur
     * @return Liste aller Mitarbeiter
     */
    @Override
    public List<BookingStaffMemberDto> listStaffMembers(String businessId) {
        log.info("Mitarbeiter werden aufgelistet für Betrieb: {}", businessId);
        ListResponse<BookingStaffMemberDto> response = graphApiRequest.get(tenantOf(businessId), 
                staffPath(businessId), ListResponse.class);
        return listeMappen(response.getValue(), BookingStaffMemberDto.class);
    }

    /**
     * Einen bestimmten Mitarbeiter abrufen.
     *
     * @param businessId     ID der Agentur
     * @param staffMemberId ID (GUID) des Mitarbeiters
     * @return Mitarbeiterdaten
     */
    @Override
    public BookingStaffMemberDto getStaffMember(String businessId, String staffMemberId) {
        log.info("Mitarbeiter {} wird abgerufen für Betrieb {}", staffMemberId, businessId);
        return graphApiRequest.get(tenantOf(businessId), staffPath(businessId) + "/" + staffMemberId,
                BookingStaffMemberDto.class);
    }

    /**
     * Neuen Mitarbeiter in einem Buchungsbetrieb anlegen.
     *
     * @param businessId ID der Agentur
     * @param request   Mitarbeiterdaten (Name, E-Mail, Rolle, Zeitzone)
     * @return Der erstellte Mitarbeiter
     */
    @Override
    public BookingStaffMemberDto createStaffMember(String businessId,
                                                      CreateStaffMemberRequest request) {
        log.info("Neuer Mitarbeiter '{}' wird angelegt in Betrieb {}", request.getDisplayName(), businessId);
        return graphApiRequest.post(tenantOf(businessId), staffPath(businessId), request, BookingStaffMemberDto.class);
    }

    /**
     * Bestehenden Mitarbeiter aktualisieren.
     *
     * @param businessId     ID der Agentur
     * @param staffMemberId ID des Mitarbeiters
     * @param request       Neue Mitarbeiterdaten
     * @return Der aktualisierte Mitarbeiter
     */
    @Override
    public BookingStaffMemberDto updateStaffMember(String businessId,
                                                          String staffMemberId,
                                                          CreateStaffMemberRequest request) {
        log.info("Mitarbeiter {} wird aktualisiert in Betrieb {}", staffMemberId, businessId);
        return graphApiRequest.patch(tenantOf(businessId), staffPath(businessId) + "/" + staffMemberId,
                request, BookingStaffMemberDto.class);
    }

    /**
     * Mitarbeiter aus einem Buchungsbetrieb entfernen.
     *
     * @param businessId     ID der Agentur
     * @param staffMemberId ID des zu entfernenden Mitarbeiters
     */
    @Override
    public void deleteStaffMember(String businessId, String staffMemberId) {
        log.info("Mitarbeiter {} wird entfernt aus Betrieb {}", staffMemberId, businessId);
        graphApiRequest.delete(tenantOf(businessId), staffPath(businessId) + "/" + staffMemberId);
    }

    /**
     * Verfügbarkeit (frei / belegt) eines oder mehrerer Mitarbeiter
     * in einem Zeitraum abfragen.
     *
     * Verfügbarkeitsstatus pro Zeitfenster:
     * - {@code Available}      → Mitarbeiter ist frei
     * - {@code Busy}           → Mitarbeiter hat einen Termin (siehe serviceId)
     * - {@code SlotsAvailable} → Freie Zeitfenster vorhanden
     * - {@code OutOfOffice}    → Mitarbeiter ist außer Haus
     *
     * @param businessId ID der Agentur
     * @param request   Anfrage mit Mitarbeiter-IDs und Zeitraum
     * @return Verfügbarkeitsliste pro Mitarbeiter
     */
    @Override
    public List<StaffAvailabilityItemDto> getStaffMemberAvailability(
            String businessId, StaffAvailabilityRequestDto request) {
        log.info("Mitarbeiterverfügbarkeit wird abgefragt in Betrieb {}: mitarbeiterIds={}",
                businessId, request.getStaffIds());

        String path = "/solutions/bookingBusinesses/" + businessId + "/getStaffAvailability";
        StaffAvailabilityResponseDto response = graphApiRequest.post(tenantOf(businessId), path, request,
                StaffAvailabilityResponseDto.class);

        return response != null ? response.getStaffAvailabilityItem() : List.of();
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
