package com.booking.azure.application.service;

import com.booking.azure.application.dto.ListResponse;
import com.booking.azure.application.command.CreateAppointmentRequest;
import com.booking.azure.domain.exception.AgencyNotFoundException;
import com.booking.azure.domain.exception.GraphResponseException;
import com.booking.azure.domain.model.Agency;
import com.booking.azure.domain.model.Booking;
import com.booking.azure.domain.model.SlotRequest;
import com.booking.azure.domain.model.vo.AgencyName;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.CustomerContact;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.StaffName;
import com.booking.azure.domain.model.vo.TimeWindow;
import com.booking.azure.application.port.in.AppointmentManagement;
import com.booking.azure.domain.port.out.AgencyRepository;
import com.booking.azure.application.port.out.GraphApiRequest;
import com.booking.azure.domain.port.out.BookingRepository;
import com.booking.azure.domain.port.out.DomainEventPublisher;
import com.booking.azure.dto.BookingAppointmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService implements AppointmentManagement {

    private final GraphApiRequest graphApiRequest;
    private final BookingRepository bookingRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private final AgencyRepository agencyRepository;
    private final DomainEventPublisher eventPublisher;

    // ───────────────────────────────── helpers ─────────────────────────────────

    private Agency resolveAgency(String agencyName) {
        AgencyName name = AgencyName.of(agencyName);
        return agencyRepository.findByName(name)
                .orElseThrow(() -> new AgencyNotFoundException(name));
    }

    /**
     * Turns the caller-facing worker names into Microsoft identifiers.
     *
     * The lookup itself belongs to the {@link Agency} aggregate; this method
     * only adapts the raw strings arriving from the request.
     */
    private List<StaffMemberId> resolveStaffIds(Agency agency, List<String> workerNames) {
        if (workerNames == null || workerNames.isEmpty()) return List.of();
        return agency.resolveStaff(workerNames.stream().map(StaffName::of).toList());
    }

    private String appointmentsPath(String businessId) {
        return "/solutions/bookingBusinesses/" + businessId + "/appointments";
    }

    private String calendarViewPath(String businessId) {
        return "/solutions/bookingBusinesses/" + businessId + "/calendarView";
    }

    // ──────────────────────────── use-case implementations ─────────────────────

    @Override
    public List<BookingAppointmentDto> listAppointments(String agencyName) {
        String businessId = resolveAgency(agencyName).businessId().value();
        log.info("Listing appointments for business: {}", businessId);
        ListResponse<BookingAppointmentDto> response = graphApiRequest.get(
                appointmentsPath(businessId), ListResponse.class);
        return mapList(response.getValue(), BookingAppointmentDto.class);
    }

    @Override
    public List<BookingAppointmentDto> getCalendarView(String agencyName,
                                                       String startDateTime,
                                                       String endDateTime) {
        String businessId = resolveAgency(agencyName).businessId().value();
        log.info("Calendar view for business {}: {} → {}", businessId, startDateTime, endDateTime);
        String path = calendarViewPath(businessId)
                + "?startDateTime=" + startDateTime
                + "&endDateTime=" + endDateTime;
        ListResponse<BookingAppointmentDto> response = graphApiRequest.get(path, ListResponse.class);
        return mapList(response.getValue(), BookingAppointmentDto.class);
    }

    @Override
    public BookingAppointmentDto getAppointment(String agencyName, String appointmentId) {
        String businessId = resolveAgency(agencyName).businessId().value();
        log.info("Retrieving appointment {} for business {}", appointmentId, businessId);
        return graphApiRequest.get(appointmentsPath(businessId) + "/" + appointmentId,
                BookingAppointmentDto.class);
    }

    @Override
    public BookingAppointmentDto createAppointment(String agencyName, CreateAppointmentRequest request) {
        Agency agency = resolveAgency(agencyName);
        String businessId = agency.businessId().value();

        log.info("Creating appointment in business {}, service: {}", businessId, request.getServiceId());

        if (request.getWorkerNames() == null || request.getWorkerNames().isEmpty()) {
            log.warn("Appointment without staff assignment in business {} – no slot reservation possible",
                    businessId);
            return graphApiRequest.post(appointmentsPath(businessId), request, BookingAppointmentDto.class);
        }

        // 1. Map names onto Microsoft identifiers
        List<StaffMemberId> staffIds = resolveStaffIds(agency, request.getWorkerNames());
        request.setStaffMemberIds(staffIds.stream().map(StaffMemberId::value).toList());

        // 2. Take the local hold (slot reservation)
        Booking booking = bookingRepository.reserve(slotRequest(businessId, request, staffIds))
                .forCustomer(customerOf(request));
        publishEventsOf(booking);

        // 3. Graph
        try {
            BookingAppointmentDto appointment = graphApiRequest.post(
                    appointmentsPath(businessId), request, BookingAppointmentDto.class);
            booking.confirm(AppointmentId.of(appointment.getId()));
            bookingRepository.save(booking);

            // 4. Announce it. Whoever cares — today the confirmation email —
            // subscribes; this use case no longer knows they exist.
            publishEventsOf(booking);

            return appointment;

        } catch (GraphResponseException ex) {
            log.error("Graph rejected the appointment (status {}), releasing booking {}: {}",
                    ex.getStatus(), booking.id(), ex.getMessage());
            booking.release();
            bookingRepository.save(booking);
            publishEventsOf(booking);
            throw ex;

        } catch (RuntimeException ex) {
            // No definitive answer: the booking deliberately stays PENDING so the
            // recovery job can ask Graph what actually happened. Releasing here
            // would free a slot that may well be taken.
            log.error("No definitive answer from Graph. Booking {} stays PENDING", booking.id());
            throw ex;
        }
    }

    /**
     * Hands whatever the aggregate recorded to the bus.
     *
     * Called only after the aggregate has been written: an event announcing a
     * confirmation that then failed to commit is a lie subscribers have already
     * acted on.
     */
    private void publishEventsOf(Booking booking) {
        eventPublisher.publishAll(booking.pullEvents());
    }

    /**
     * Reads the customer off the incoming request, if it named one.
     *
     * Returns {@code null} rather than a placeholder: the old code substituted
     * the literal string {@code "Unknown"} for a missing name and email, which
     * produced confirmation emails addressed to nobody at an invalid address.
     * Absent is absent.
     */
    private CustomerContact customerOf(CreateAppointmentRequest request) {
        if (request.getCustomers() == null || request.getCustomers().isEmpty()) {
            return null;
        }
        var first = request.getCustomers().get(0);
        if (first.getName() == null || first.getName().isBlank()
                || first.getEmailAddress() == null || first.getEmailAddress().isBlank()) {
            return null;
        }
        return CustomerContact.of(first.getName(), first.getEmailAddress());
    }

    @Override
    public BookingAppointmentDto updateAppointment(String agencyName,
                                                     String appointmentId,
                                                     CreateAppointmentRequest request) {
        Agency agency = resolveAgency(agencyName);
        String businessId = agency.businessId().value();

        log.info("Updating appointment {} in business {}", appointmentId, businessId);

        if (request.getWorkerNames() == null || request.getWorkerNames().isEmpty()) {
            return graphApiRequest.patch(appointmentsPath(businessId) + "/" + appointmentId,
                    request, BookingAppointmentDto.class);
        }

        List<StaffMemberId> staffIds = resolveStaffIds(agency, request.getWorkerNames());
        request.setStaffMemberIds(staffIds.stream().map(StaffMemberId::value).toList());

        AppointmentId id = AppointmentId.of(appointmentId);
        Booking booking = bookingRepository.reschedule(id, slotRequest(businessId, request, staffIds));

        try {
            BookingAppointmentDto appointment = graphApiRequest.patch(
                    appointmentsPath(businessId) + "/" + appointmentId, request, BookingAppointmentDto.class);
            booking.confirm(id);
            bookingRepository.save(booking);
            publishEventsOf(booking);
            return appointment;

        } catch (GraphResponseException ex) {
            booking.release();
            bookingRepository.save(booking);
            publishEventsOf(booking);
            throw ex;
        }
    }

    @Override
    public void cancelAppointment(String agencyName, String appointmentId) {
        String businessId = resolveAgency(agencyName).businessId().value();
        log.info("Cancelling appointment {} in business {}", appointmentId, businessId);

        graphApiRequest.delete(appointmentsPath(businessId) + "/" + appointmentId);

        bookingRepository.findBlockingByAppointmentId(AppointmentId.of(appointmentId))
                .ifPresent(booking -> {
                    booking.release();
                    bookingRepository.save(booking);
                    publishEventsOf(booking);
                });
    }

    private SlotRequest slotRequest(String businessId, CreateAppointmentRequest request,
                                    List<StaffMemberId> staffIds) {
        TimeWindow window = TimeWindow.of(
                TimeZoneConverter.toInstant(request.getStartDateTime()),
                TimeZoneConverter.toInstant(request.getEndDateTime()));

        return new SlotRequest(
                BusinessId.of(businessId),
                ServiceId.of(request.getServiceId()),
                staffIds,
                window);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> mapList(List<?> rawList, Class<T> targetType) {
        if (rawList == null) return List.of();
        return objectMapper.convertValue(rawList,
                objectMapper.getTypeFactory().constructCollectionType(List.class, targetType));
    }
}
