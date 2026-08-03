package com.booking.azure.domain.event;

import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BookingId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.CustomerContact;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;

import java.util.List;
import java.util.Optional;

/**
 * Microsoft Graph accepted the appointment; the booking's slots are settled.
 *
 * <p>The customer contact is optional on purpose. A booking confirmed by the
 * recovery job is rebuilt from stored reservation rows, which do not carry
 * customer details — the system deliberately does not keep a second copy of that
 * personal data, since Microsoft Bookings is its owner. Subscribers that need a
 * customer must therefore tolerate its absence, and a recovery confirmation
 * correctly sends no email: one was already sent, or the booking never had a
 * customer to write to.
 */
public final class BookingConfirmed extends BaseDomainEvent {

    private final BookingId bookingId;
    private final AppointmentId appointmentId;
    private final BusinessId businessId;
    private final ServiceId serviceId;
    private final TimeWindow window;
    private final List<StaffMemberId> staffMemberIds;
    private final CustomerContact customer;

    public BookingConfirmed(BookingId bookingId,
                            AppointmentId appointmentId,
                            BusinessId businessId,
                            ServiceId serviceId,
                            TimeWindow window,
                            List<StaffMemberId> staffMemberIds,
                            CustomerContact customer) {
        super(bookingId.value());
        this.bookingId = bookingId;
        this.appointmentId = appointmentId;
        this.businessId = businessId;
        this.serviceId = serviceId;
        this.window = window;
        this.staffMemberIds = List.copyOf(staffMemberIds);
        this.customer = customer;
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public AppointmentId appointmentId() {
        return appointmentId;
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

    public Optional<CustomerContact> customer() {
        return Optional.ofNullable(customer);
    }
}
