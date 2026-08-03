package com.booking.azure.domain.event;

import com.booking.azure.domain.model.vo.BookingId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;

import java.util.List;

/** Slots were taken for a booking, before Microsoft Graph knows about it. */
public final class SlotsReserved extends BaseDomainEvent {

    private final BookingId bookingId;
    private final BusinessId businessId;
    private final ServiceId serviceId;
    private final TimeWindow window;
    private final List<StaffMemberId> staffMemberIds;

    public SlotsReserved(BookingId bookingId,
                         BusinessId businessId,
                         ServiceId serviceId,
                         TimeWindow window,
                         List<StaffMemberId> staffMemberIds) {
        super(bookingId.value());
        this.bookingId = bookingId;
        this.businessId = businessId;
        this.serviceId = serviceId;
        this.window = window;
        this.staffMemberIds = List.copyOf(staffMemberIds);
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public BusinessId businessId() {
        return businessId;
    }

    public ServiceId serviceId() {
        return serviceId;
    }

    public TimeWindow window() {
        return window;
    }

    public List<StaffMemberId> staffMemberIds() {
        return staffMemberIds;
    }
}
