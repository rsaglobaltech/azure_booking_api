package com.booking.azure.infrastructure.adapter.out.graph;

import com.booking.azure.application.port.out.AppointmentCalendarPort;
import com.booking.azure.application.port.out.GraphApiRequest;
import com.booking.azure.domain.model.AppointmentDraft;
import com.booking.azure.domain.model.vo.AppointmentCustomer;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.ServiceLocation;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.dto.BookingAppointmentDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Anti-corruption layer between the booking model and Microsoft Graph.
 *
 * This is the only class that knows what JSON {@code bookingAppointment}
 * expects. Everything above it works with {@link AppointmentDraft}, so a change
 * to Graph's contract stops here instead of reaching the domain — which is what
 * happened before, when the inbound HTTP request object was forwarded to Graph
 * unchanged and both contracts were the same object.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphAppointmentAdapter implements AppointmentCalendarPort {

    private final GraphApiRequest graphApiRequest;

    @Override
    public BookingAppointmentDto create(BusinessId businessId, AppointmentDraft draft) {
        return graphApiRequest.post(appointmentsPath(businessId), toPayload(draft),
                BookingAppointmentDto.class);
    }

    @Override
    public BookingAppointmentDto update(BusinessId businessId, AppointmentId appointmentId,
                                        AppointmentDraft draft) {
        return graphApiRequest.patch(appointmentPath(businessId, appointmentId), toPayload(draft),
                BookingAppointmentDto.class);
    }

    @Override
    public void cancel(BusinessId businessId, AppointmentId appointmentId) {
        graphApiRequest.delete(appointmentPath(businessId, appointmentId));
    }

    private String appointmentsPath(BusinessId businessId) {
        return "/solutions/bookingBusinesses/" + businessId.value() + "/appointments";
    }

    private String appointmentPath(BusinessId businessId, AppointmentId appointmentId) {
        return appointmentsPath(businessId) + "/" + appointmentId.value();
    }

    // ─────────────────────────────── translation ───────────────────────────────

    private GraphAppointment toPayload(AppointmentDraft draft) {
        return new GraphAppointment(
                draft.serviceId().value(),
                toGraphTime(draft.window().start(), draft.zone()),
                toGraphTime(draft.window().end(), draft.zone()),
                draft.staffMemberIds().stream().map(StaffMemberId::value).toList(),
                draft.customerDetails().map(this::toGraphCustomer).map(List::of).orElse(null),
                draft.serviceNotes(),
                draft.additionalInformation(),
                draft.serviceLocation().map(this::toGraphLocation).orElse(null),
                draft.onlineMeeting(),
                draft.suppressCustomerEmail());
    }

    /**
     * Renders the instant back into the zone the caller used.
     *
     * The window is held in UTC because that is the only form in which overlaps
     * can be compared, but sending UTC to Graph would rewrite every appointment
     * away from the wall-clock time the customer actually booked. The instant is
     * the same either way; the representation is not, and Bookings displays what
     * it is given.
     */
    private GraphDateTime toGraphTime(Instant instant, ZoneId zone) {
        LocalDateTime local = LocalDateTime.ofInstant(instant, zone);
        return new GraphDateTime(local.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), zone.getId());
    }

    private GraphCustomer toGraphCustomer(AppointmentCustomer customer) {
        return new GraphCustomer(
                customer.customerId(),
                customer.contact().name(),
                customer.contact().email(),
                customer.phone(),
                customer.notes());
    }

    private GraphLocation toGraphLocation(ServiceLocation location) {
        GraphAddress address = location.hasAddress()
                ? new GraphAddress(location.street(), location.city(), location.state(),
                        location.postalCode(), location.countryOrRegion())
                : null;
        return new GraphLocation(location.displayName(), address);
    }

    // ──────────────────────── Graph wire format (this layer only) ──────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GraphAppointment(
            @JsonProperty("serviceId") String serviceId,
            @JsonProperty("startDateTime") GraphDateTime startDateTime,
            @JsonProperty("endDateTime") GraphDateTime endDateTime,
            @JsonProperty("staffMemberIds") List<String> staffMemberIds,
            @JsonProperty("customers") List<GraphCustomer> customers,
            @JsonProperty("serviceNotes") String serviceNotes,
            @JsonProperty("additionalInformation") String additionalInformation,
            @JsonProperty("serviceLocation") GraphLocation serviceLocation,
            @JsonProperty("isLocationOnline") Boolean isLocationOnline,
            @JsonProperty("optOutOfCustomerEmail") Boolean optOutOfCustomerEmail) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GraphDateTime(
            @JsonProperty("dateTime") String dateTime,
            @JsonProperty("timeZone") String timeZone) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GraphCustomer(
            @JsonProperty("customerId") String customerId,
            @JsonProperty("name") String name,
            @JsonProperty("emailAddress") String emailAddress,
            @JsonProperty("phone") String phone,
            @JsonProperty("notes") String notes) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GraphLocation(
            @JsonProperty("displayName") String displayName,
            @JsonProperty("address") GraphAddress address) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GraphAddress(
            @JsonProperty("street") String street,
            @JsonProperty("city") String city,
            @JsonProperty("state") String state,
            @JsonProperty("postalCode") String postalCode,
            @JsonProperty("countryOrRegion") String countryOrRegion) {
    }
}
