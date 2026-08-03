package com.booking.azure.domain.event;

import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BookingId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;

import java.util.List;
import java.util.Optional;

/**
 * A booking gave its slots back — through cancellation, compensation after a
 * rejected Graph call, or a reschedule onto a different window.
 *
 * <p>The appointment id is absent when the booking never reached Graph.
 */
public final class BookingReleased extends BaseDomainEvent {

    private final BookingId bookingId;
    private final BusinessId businessId;
    private final TimeWindow window;
    private final List<StaffMemberId> staffMemberIds;
    private final AppointmentId appointmentId;

    public BookingReleased(BookingId bookingId,
                           BusinessId businessId,
                           TimeWindow window,
                           List<StaffMemberId> staffMemberIds,
                           AppointmentId appointmentId) {
        super(bookingId.value());
        this.bookingId = bookingId;
        this.businessId = businessId;
        this.window = window;
        this.staffMemberIds = List.copyOf(staffMemberIds);
        this.appointmentId = appointmentId;
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public BusinessId businessId() {
        return businessId;
    }

    public TimeWindow window() {
        return window;
    }

    public List<StaffMemberId> staffMemberIds() {
        return staffMemberIds;
    }

    public Optional<AppointmentId> appointmentId() {
        return Optional.ofNullable(appointmentId);
    }
}
