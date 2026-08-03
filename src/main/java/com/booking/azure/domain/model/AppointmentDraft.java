package com.booking.azure.domain.model;

import com.booking.azure.domain.model.vo.AppointmentCustomer;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.ServiceLocation;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * What the system intends to write to the calendar, stated in domain terms.
 *
 * <h2>Why this exists</h2>
 *
 * The request object arriving from HTTP used to be forwarded to Microsoft Graph
 * unchanged, {@code @JsonProperty} annotations and all. Microsoft's JSON shape
 * was therefore also this system's inbound API shape, and neither could change
 * without the other. A draft breaks that: callers describe an appointment, and
 * an adapter decides how Graph wants to hear it.
 *
 * <h2>Why the zone is carried separately</h2>
 *
 * {@link #window} is UTC, because that is the only form in which two bookings
 * can be compared for overlap. But UTC is not how the appointment was <i>meant</i>
 * — a customer books "10:00 in Berlin", and that is what the calendar should
 * show. Rendering the window back into {@link #zone} reproduces the wall-clock
 * time the caller sent, rather than silently rewriting every appointment into
 * UTC on its way out.
 *
 * @param serviceId      the service being booked
 * @param window         the slot, normalised to UTC
 * @param zone           the zone the caller expressed the appointment in
 * @param staffMemberIds the assigned staff, already resolved to Microsoft ids
 */
public record AppointmentDraft(
        ServiceId serviceId,
        TimeWindow window,
        ZoneId zone,
        List<StaffMemberId> staffMemberIds,
        AppointmentCustomer customer,
        String serviceNotes,
        String additionalInformation,
        ServiceLocation location,
        Boolean onlineMeeting,
        Boolean suppressCustomerEmail) {

    public AppointmentDraft {
        if (serviceId == null) {
            throw new IllegalArgumentException("serviceId is required");
        }
        if (window == null) {
            throw new IllegalArgumentException("window is required");
        }
        if (zone == null) {
            throw new IllegalArgumentException("zone is required");
        }
        staffMemberIds = staffMemberIds == null ? List.of() : List.copyOf(staffMemberIds);
    }

    public Optional<AppointmentCustomer> customerDetails() {
        return Optional.ofNullable(customer);
    }

    public Optional<ServiceLocation> serviceLocation() {
        return Optional.ofNullable(location).filter(value -> !value.isEmpty());
    }
}
