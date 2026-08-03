package com.booking.azure.application.service;

import com.booking.azure.application.dto.ListResponse;
import com.booking.azure.application.command.CreateAppointmentRequest;
import com.booking.azure.domain.exception.AgencyNotFoundException;
import com.booking.azure.domain.exception.GraphResponseException;
import com.booking.azure.domain.model.AppointmentDraft;
import com.booking.azure.domain.model.vo.AppointmentCustomer;
import com.booking.azure.domain.model.vo.ServiceLocation;
import com.booking.azure.dto.LocationDto;
import com.booking.azure.dto.PhysicalAddressDto;
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
import com.booking.azure.application.port.out.AppointmentCalendarPort;
import com.booking.azure.application.port.out.GraphApiRequest;
import com.booking.azure.domain.port.out.BookingRepository;
import com.booking.azure.domain.port.out.DomainEventPublisher;
import com.booking.azure.dto.BookingAppointmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService implements AppointmentManagement {

    private final GraphApiRequest graphApiRequest;
    private final BookingRepository bookingRepository;
    private final AppointmentCalendarPort calendarPort;
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
        Agency agency = resolveAgency(agencyName);
        String businessId = agency.businessId().value();
        log.info("Listing appointments for business: {}", businessId);
        ListResponse<BookingAppointmentDto> response = graphApiRequest.get(
                agency.tenantId(), appointmentsPath(businessId), ListResponse.class);
        return mapList(response.getValue(), BookingAppointmentDto.class);
    }

    @Override
    public List<BookingAppointmentDto> getCalendarView(String agencyName,
                                                       String startDateTime,
                                                       String endDateTime) {
        Agency agency = resolveAgency(agencyName);
        String businessId = agency.businessId().value();
        log.info("Calendar view for business {}: {} → {}", businessId, startDateTime, endDateTime);
        String path = calendarViewPath(businessId)
                + "?startDateTime=" + startDateTime
                + "&endDateTime=" + endDateTime;
        ListResponse<BookingAppointmentDto> response =
                graphApiRequest.get(agency.tenantId(), path, ListResponse.class);
        return mapList(response.getValue(), BookingAppointmentDto.class);
    }

    @Override
    public BookingAppointmentDto getAppointment(String agencyName, String appointmentId) {
        Agency agency = resolveAgency(agencyName);
        String businessId = agency.businessId().value();
        log.info("Retrieving appointment {} for business {}", appointmentId, businessId);
        return graphApiRequest.get(agency.tenantId(),
                appointmentsPath(businessId) + "/" + appointmentId, BookingAppointmentDto.class);
    }

    @Override
    public BookingAppointmentDto createAppointment(String agencyName, CreateAppointmentRequest request) {
        Agency agency = resolveAgency(agencyName);
        String businessId = agency.businessId().value();

        log.info("Creating appointment in business {}, service: {}", businessId, request.getServiceId());

        if (request.getWorkerNames() == null || request.getWorkerNames().isEmpty()) {
            log.warn("Appointment without staff assignment in business {} – no slot reservation possible",
                    businessId);
            return calendarPort.create(agency.tenantId(), agency.businessId(), draftOf(request, List.of()));
        }

        // 1. Traducir nombres a identificadores de Microsoft
        List<StaffMemberId> staffIds = resolveStaffIds(agency, request.getWorkerNames());

        // 2. Retener el hueco localmente ANTES de tocar Graph.
        //    Aquí se decide la exclusión mutua: si otra petición ya se llevó
        //    este hueco, reserve() lanza SlotConflictException (409) y no se
        //    llega a llamar a Graph. Véase BookingJpaAdapter#store.
        Booking booking = bookingRepository.reserve(slotRequest(businessId, request, staffIds))
                .forCustomer(customerOf(request));
        publishEventsOf(booking);

        // 3. Graph. Fuera de toda transacción: mantener una abierta durante
        //    una llamada de red agota el pool de conexiones bajo carga.
        try {
            BookingAppointmentDto appointment =
                    calendarPort.create(agency.tenantId(), agency.businessId(), draftOf(request, staffIds));
            booking.confirm(AppointmentId.of(appointment.getId()));
            bookingRepository.save(booking);

            // 4. Anunciarlo. Quien tenga interés —hoy, el correo de
            //    confirmación— se suscribe; este caso de uso ya no sabe que
            //    existen.
            publishEventsOf(booking);

            return appointment;

        } catch (GraphResponseException ex) {
            // Graph respondió y rechazó: hay certeza de que no se creó nada,
            // así que se compensa liberando el hueco.
            log.error("Graph rejected the appointment (status {}), releasing booking {}: {}",
                    ex.getStatus(), booking.id(), ex.getMessage());
            booking.release();
            bookingRepository.save(booking);
            publishEventsOf(booking);
            throw ex;

        } catch (RuntimeException ex) {
            // Sin respuesta definitiva de Graph: la reserva se queda en PENDING
            // A PROPÓSITO, para que el job de recuperación pregunte a Graph qué
            // ocurrió en realidad.
            //
            // Liberar aquí es el error clásico: un timeout del cliente no aborta
            // el trabajo del servidor, así que el POST puede haber creado la cita
            // igualmente. Liberar el hueco lo dejaría libre para otro cliente y
            // acabaríamos con dos citas solapadas.
            //
            // Un hueco retenido de más es el fallo barato; una doble reserva, el
            // caro.
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

        AppointmentId id = AppointmentId.of(appointmentId);

        if (request.getWorkerNames() == null || request.getWorkerNames().isEmpty()) {
            return calendarPort.update(agency.tenantId(), agency.businessId(), id, draftOf(request, List.of()));
        }

        List<StaffMemberId> staffIds = resolveStaffIds(agency, request.getWorkerNames());
        Booking booking = bookingRepository.reschedule(id, slotRequest(businessId, request, staffIds));

        try {
            BookingAppointmentDto appointment =
                    calendarPort.update(agency.tenantId(), agency.businessId(), id, draftOf(request, staffIds));
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
        Agency agency = resolveAgency(agencyName);
        log.info("Cancelling appointment {} in business {}", appointmentId, agency.businessId());

        calendarPort.cancel(agency.tenantId(), agency.businessId(), AppointmentId.of(appointmentId));

        bookingRepository.findBlockingByAppointmentId(AppointmentId.of(appointmentId))
                .ifPresent(booking -> {
                    booking.release();
                    bookingRepository.save(booking);
                    publishEventsOf(booking);
                });
    }

    /**
     * Turns the incoming request into the domain's description of the
     * appointment to write.
     *
     * This is where the HTTP shape stops. Previously the request object itself
     * travelled all the way to Microsoft Graph, so its field names were
     * simultaneously this API's contract and Microsoft's.
     */
    private AppointmentDraft draftOf(CreateAppointmentRequest request, List<StaffMemberId> staffIds) {
        return new AppointmentDraft(
                ServiceId.of(request.getServiceId()),
                windowOf(request),
                zoneOf(request),
                staffIds,
                appointmentCustomerOf(request),
                request.getServiceNotes(),
                request.getAdditionalInformation(),
                locationOf(request),
                request.getIsLocationOnline(),
                request.getOptOutOfCustomerEmail());
    }

    private TimeWindow windowOf(CreateAppointmentRequest request) {
        return TimeWindow.of(
                TimeZoneConverter.toInstant(request.getStartDateTime()),
                TimeZoneConverter.toInstant(request.getEndDateTime()));
    }

    /**
     * The zone the caller expressed the appointment in.
     *
     * Kept so the outgoing payload can reproduce the wall-clock time that was
     * booked. Falls back to UTC when the caller sent an absolute timestamp and
     * named no zone — then the instant is unambiguous and any rendering is
     * faithful.
     */
    private ZoneId zoneOf(CreateAppointmentRequest request) {
        String zone = request.getStartDateTime() == null ? null : request.getStartDateTime().getTimeZone();
        if (zone == null || zone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(zone.trim());
        } catch (RuntimeException ex) {
            // TimeZoneConverter already rejects unusable zones when it needs
            // them. Reaching here means the timestamp carried its own offset, so
            // UTC loses nothing.
            return ZoneOffset.UTC;
        }
    }

    private AppointmentCustomer appointmentCustomerOf(CreateAppointmentRequest request) {
        CustomerContact contact = customerOf(request);
        if (contact == null) {
            return null;
        }
        var first = request.getCustomers().get(0);
        return new AppointmentCustomer(contact, first.getCustomerId(), first.getPhone(), first.getNotes());
    }

    private ServiceLocation locationOf(CreateAppointmentRequest request) {
        LocationDto location = request.getServiceLocation();
        if (location == null) {
            return null;
        }
        PhysicalAddressDto address = location.getAddress();
        return new ServiceLocation(
                location.getDisplayName(),
                address == null ? null : address.getStreet(),
                address == null ? null : address.getCity(),
                address == null ? null : address.getState(),
                address == null ? null : address.getPostalCode(),
                address == null ? null : address.getCountryOrRegion());
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
