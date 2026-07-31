package com.booking.azure.application.service;

import com.booking.azure.domain.exception.GraphResponseException;
import com.booking.azure.domain.model.SlotRequest;
import com.booking.azure.domain.model.AgencyMapping;
import com.booking.azure.domain.model.StaffMapping;
import com.booking.azure.domain.model.BookingDetails;
import com.booking.azure.domain.port.BookingNotificationPort;
import com.booking.azure.domain.port.in.AppointmentManagement;
import com.booking.azure.domain.port.out.GraphApiRequest;
import com.booking.azure.domain.port.out.SlotReservationPort;
import com.booking.azure.dto.BookingAppointmentDto;
import com.booking.azure.application.dto.ListResponse;
import com.booking.azure.domain.command.CreateAppointmentRequest;
import com.booking.azure.domain.port.out.AgencyRepository;
import com.booking.azure.domain.port.out.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService implements AppointmentManagement {

    private final GraphApiRequest graphApiRequest;
    private final SlotReservationPort slotReservation;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    
    private final AgencyRepository agencyMappingRepository;
    private final StaffRepository staffMappingRepository;
    private final BookingNotificationPort notificationPort;

    // ──────────────────────────────── Hilfsmethoden ────────────────────────────

    private AgencyMapping resolveAgency(String agencyName) {
        return agencyMappingRepository.findByFriendlyName(agencyName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency not found: " + agencyName));
    }

    private List<String> resolveStaffIds(AgencyMapping agency, List<String> workerNames) {
        if (workerNames == null || workerNames.isEmpty()) return List.of();
        List<String> staffIds = new ArrayList<>();
        for (String name : workerNames) {
            String staffId = staffMappingRepository.findByAgencyIdAndFriendlyName(agency.getId(), name)
                    .map(StaffMapping::getMsStaffMemberId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found: " + name));
            staffIds.add(staffId);
        }
        return staffIds;
    }

    private String appointmentsPath(String businessId) {
        return "/solutions/bookingBusinesses/" + businessId + "/appointments";
    }

    private String kalenderPfad(String businessId) {
        return "/solutions/bookingBusinesses/" + businessId + "/calendarView";
    }

    // ──────────────────────────── Use-Case-Implementierungen ────────────────────

    @Override
    public List<BookingAppointmentDto> listAppointments(String agencyName) {
        String businessId = resolveAgency(agencyName).getMsBusinessId();
        log.info("Termine werden aufgelistet für Betrieb: {}", businessId);
        ListResponse<BookingAppointmentDto> antwort = graphApiRequest.get(
                appointmentsPath(businessId), ListResponse.class);
        return listeMappen(antwort.getValue(), BookingAppointmentDto.class);
    }

    @Override
    public List<BookingAppointmentDto> kalenderAnsichtAbrufen(String agencyName,
                                                              String startDatumZeit,
                                                              String endDatumZeit) {
        String businessId = resolveAgency(agencyName).getMsBusinessId();
        log.info("Kalenderansicht für Betrieb {}: {} → {}", businessId, startDatumZeit, endDatumZeit);
        String path = kalenderPfad(businessId)
                + "?startDateTime=" + startDatumZeit
                + "&endDateTime=" + endDatumZeit;
        ListResponse<BookingAppointmentDto> antwort = graphApiRequest.get(path, ListResponse.class);
        return listeMappen(antwort.getValue(), BookingAppointmentDto.class);
    }

    @Override
    public BookingAppointmentDto getAppointment(String agencyName, String appointmentId) {
        String businessId = resolveAgency(agencyName).getMsBusinessId();
        log.info("Termin {} wird abgerufen für Betrieb {}", appointmentId, businessId);
        return graphApiRequest.get(appointmentsPath(businessId) + "/" + appointmentId,
                BookingAppointmentDto.class);
    }

    @Override
    public BookingAppointmentDto createAppointment(String agencyName, CreateAppointmentRequest request) {
        AgencyMapping agency = resolveAgency(agencyName);
        String businessId = agency.getMsBusinessId();
        
        log.info("Neuer Termin wird erstellt in Betrieb {}, Dienst: {}", businessId, request.getServiceId());

        if (request.getWorkerNames() == null || request.getWorkerNames().isEmpty()) {
            log.warn("Termin ohne Mitarbeiterzuordnung in Betrieb {} – keine Slot-Reservierung möglich", businessId);
            return graphApiRequest.post(appointmentsPath(businessId), request, BookingAppointmentDto.class);
        }

        // 1. Mapeo
        List<String> staffIds = resolveStaffIds(agency, request.getWorkerNames());
        request.setStaffMemberIds(staffIds); // Set for Graph API
        
        // 2. Bloqueo Local (Slot Reservation)
        List<Long> reservierungen = slotReservation.reserve(slotRequest(businessId, request));

        // 3. Graph API
        try {
            BookingAppointmentDto termin = graphApiRequest.post(
                    appointmentsPath(businessId), request, BookingAppointmentDto.class);
            slotReservation.confirm(reservierungen, termin.getId());
            
            // 4. Notificaciones
            sendNotifications(agency, request, termin);
            
            return termin;

        } catch (GraphResponseException ex) {
            log.error("Graph lehnte den Termin ab (Status {}), Reservierung {} wird freigegeben: {}",
                    ex.getStatus(), reservierungen, ex.getMessage());
            slotReservation.release(reservierungen);
            throw ex;

        } catch (RuntimeException ex) {
            log.error("Kein definitives Ergebnis von Graph. Reservierung {} bleibt PENDING", reservierungen);
            throw ex;
        }
    }

    private void sendNotifications(AgencyMapping agency, CreateAppointmentRequest request, BookingAppointmentDto termin) {
        try {
            BookingDetails details = BookingDetails.builder()
                .agencyName(agency.getFriendlyName())
                .agencyEmail(agency.getMsBusinessId())
                .customerName(request.getCustomers() != null && !request.getCustomers().isEmpty() ? request.getCustomers().get(0).getName() : "Unknown")
                .customerEmail(request.getCustomers() != null && !request.getCustomers().isEmpty() ? request.getCustomers().get(0).getEmailAddress() : "Unknown")
                .workerName(request.getWorkerNames().get(0))
                .serviceName(request.getServiceId())
                .startTime(TimeZoneConverter.toInstant(request.getStartDateTime()).atOffset(ZoneOffset.UTC))
                .build();
                
            notificationPort.notifyBookingConfirmed(details);
        } catch (Exception e) {
            log.error("Failed to trigger notifications", e);
        }
    }

    @Override
    public BookingAppointmentDto updateAppointment(String agencyName,
                                                     String appointmentId,
                                                     CreateAppointmentRequest request) {
        AgencyMapping agency = resolveAgency(agencyName);
        String businessId = agency.getMsBusinessId();
        
        log.info("Termin {} wird aktualisiert in Betrieb {}", appointmentId, businessId);

        if (request.getWorkerNames() == null || request.getWorkerNames().isEmpty()) {
            return graphApiRequest.patch(appointmentsPath(businessId) + "/" + appointmentId,
                    request, BookingAppointmentDto.class);
        }

        List<String> staffIds = resolveStaffIds(agency, request.getWorkerNames());
        request.setStaffMemberIds(staffIds);

        List<Long> neueReservierungen =
                slotReservation.reschedule(appointmentId, slotRequest(businessId, request));

        try {
            BookingAppointmentDto termin = graphApiRequest.patch(
                    appointmentsPath(businessId) + "/" + appointmentId, request, BookingAppointmentDto.class);
            slotReservation.confirm(neueReservierungen, appointmentId);
            return termin;

        } catch (GraphResponseException ex) {
            slotReservation.release(neueReservierungen);
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    @Override
    public void cancelAppointment(String agencyName, String appointmentId) {
        String businessId = resolveAgency(agencyName).getMsBusinessId();
        log.info("Termin {} wird storniert in Betrieb {}", appointmentId, businessId);

        graphApiRequest.delete(appointmentsPath(businessId) + "/" + appointmentId);
        slotReservation.freigebenNachTerminId(appointmentId);
    }

    private SlotRequest slotRequest(String businessId, CreateAppointmentRequest request) {
        Instant start = TimeZoneConverter.toInstant(request.getStartDateTime());
        Instant ende = TimeZoneConverter.toInstant(request.getEndDateTime());

        return new SlotRequest(
                businessId,
                request.getServiceId(),
                request.getStaffMemberIds(),
                start,
                ende);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> listeMappen(List<?> rohliste, Class<T> zielklasse) {
        if (rohliste == null) return List.of();
        return objectMapper.convertValue(rohliste,
                objectMapper.getTypeFactory().constructCollectionType(List.class, zielklasse));
    }
}


