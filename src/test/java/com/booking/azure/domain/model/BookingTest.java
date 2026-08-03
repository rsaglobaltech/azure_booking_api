package com.booking.azure.domain.model;

import com.booking.azure.domain.event.BookingConfirmed;
import com.booking.azure.domain.event.BookingReleased;
import com.booking.azure.domain.event.DomainEvent;
import com.booking.azure.domain.event.SlotsReserved;
import com.booking.azure.domain.exception.IllegalBookingStateException;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.CustomerContact;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;
import org.testng.annotations.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The booking lifecycle, exercised without a database or a Spring context.
 *
 * These rules used to live in the JPA adapter as bare {@code setState} calls,
 * where nothing prevented a released row from being confirmed again.
 */
public class BookingTest {

    private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");
    private static final TimeWindow WINDOW = TimeWindow.of(START, START.plus(Duration.ofHours(1)));
    private static final Instant EXPIRES = START.plus(Duration.ofSeconds(90));
    private static final AppointmentId APPOINTMENT = AppointmentId.of("appointment-1");

    private Booking twoStaffBooking() {
        SlotRequest request = new SlotRequest(
                BusinessId.of("business-1"),
                ServiceId.of("service-1"),
                List.of(StaffMemberId.of("staff-a"), StaffMemberId.of("staff-b")),
                WINDOW);
        return Booking.request(request, EXPIRES);
    }

    @Test(description = "A new booking holds one PENDING reservation per staff member")
    public void requestHoldsOneReservationPerStaffMember() {
        Booking booking = twoStaffBooking();

        assertThat(booking.reservations()).hasSize(2);
        assertThat(booking.status()).isEqualTo(SlotStatus.PENDING);
        assertThat(booking.isBlocking()).isTrue();
        assertThat(booking.appointmentId()).isNull();
    }

    @Test(description = "Confirming settles every reservation at once — never half the booking")
    public void confirmAppliesToAllReservations() {
        Booking booking = twoStaffBooking();

        booking.confirm(APPOINTMENT);

        assertThat(booking.status()).isEqualTo(SlotStatus.CONFIRMED);
        assertThat(booking.appointmentId()).isEqualTo(APPOINTMENT);
        assertThat(booking.reservations())
                .allMatch(reservation -> reservation.status() == SlotStatus.CONFIRMED)
                .allMatch(reservation -> APPOINTMENT.equals(reservation.appointmentId()));
    }

    @Test(description = "Releasing frees every reservation and stops blocking the slot")
    public void releaseAppliesToAllReservations() {
        Booking booking = twoStaffBooking();

        booking.release();

        assertThat(booking.status()).isEqualTo(SlotStatus.RELEASED);
        assertThat(booking.isBlocking()).isFalse();
        assertThat(booking.reservations())
                .allMatch(reservation -> reservation.status() == SlotStatus.RELEASED);
    }

    @Test(description = "A released booking cannot be confirmed — that is the double-booking state")
    public void confirmingAReleasedBookingIsRejected() {
        Booking booking = twoStaffBooking();
        booking.release();

        assertThatThrownBy(() -> booking.confirm(APPOINTMENT))
                .isInstanceOf(IllegalBookingStateException.class)
                .hasMessageContaining("RELEASED");
    }

    @Test(description = "Confirming twice with the same appointment is allowed: recovery may race a live request")
    public void confirmIsIdempotentForTheSameAppointment() {
        Booking booking = twoStaffBooking();

        booking.confirm(APPOINTMENT);
        booking.confirm(APPOINTMENT);

        assertThat(booking.status()).isEqualTo(SlotStatus.CONFIRMED);
    }

    @Test(description = "Binding a second, different appointment to one booking is rejected")
    public void confirmWithADifferentAppointmentIsRejected() {
        Booking booking = twoStaffBooking();
        booking.confirm(APPOINTMENT);

        assertThatThrownBy(() -> booking.confirm(AppointmentId.of("appointment-2")))
                .isInstanceOf(IllegalBookingStateException.class);
    }

    @Test(description = "Releasing twice is harmless — compensation paths can run more than once")
    public void releaseIsIdempotent() {
        Booking booking = twoStaffBooking();

        booking.release();
        booking.pullEvents();
        booking.release();

        assertThat(booking.status()).isEqualTo(SlotStatus.RELEASED);
        assertThat(booking.hasEvents())
                .describedAs("a no-op release must not announce a second release")
                .isFalse();
    }

    @Test(description = "A released booking no longer overlaps anything: it holds nothing")
    public void releasedBookingDoesNotOverlap() {
        Booking booking = twoStaffBooking();
        assertThat(booking.overlaps(WINDOW)).isTrue();

        booking.release();

        assertThat(booking.overlaps(WINDOW)).isFalse();
    }

    @Test(description = "A rescheduled booking knows its appointment but stays PENDING until Graph confirms")
    public void rescheduleOfStaysPending() {
        SlotRequest request = new SlotRequest(
                BusinessId.of("business-1"), ServiceId.of("service-1"),
                List.of(StaffMemberId.of("staff-a")), WINDOW);

        Booking booking = Booking.rescheduleOf(request, EXPIRES, APPOINTMENT);

        assertThat(booking.appointmentId()).isEqualTo(APPOINTMENT);
        assertThat(booking.status())
                .describedAs("CONFIRMED here would hide the booking from the recovery job")
                .isEqualTo(SlotStatus.PENDING);
    }

    // ─────────────────────────────── events ────────────────────────────────

    @Test(description = "Requesting, confirming and releasing each announce themselves")
    public void lifecycleRegistersEvents() {
        Booking booking = twoStaffBooking();
        assertThat(booking.pullEvents()).hasExactlyElementsOfTypes(SlotsReserved.class);

        booking.confirm(APPOINTMENT);
        assertThat(booking.pullEvents()).hasExactlyElementsOfTypes(BookingConfirmed.class);

        booking.release();
        assertThat(booking.pullEvents()).hasExactlyElementsOfTypes(BookingReleased.class);
    }

    @Test(description = "Pulling drains: a redelivered confirmation would send a second email")
    public void pullEventsDrains() {
        Booking booking = twoStaffBooking();

        List<DomainEvent> first = booking.pullEvents();
        List<DomainEvent> second = booking.pullEvents();

        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
        assertThat(booking.hasEvents()).isFalse();
    }

    @Test(description = "The confirmation event carries the customer when one was attached")
    public void confirmedEventCarriesTheCustomer() {
        Booking booking = twoStaffBooking()
                .forCustomer(CustomerContact.of("Ada", "ada@example.com"));
        booking.pullEvents();

        booking.confirm(APPOINTMENT);

        BookingConfirmed event = (BookingConfirmed) booking.pullEvents().get(0);
        assertThat(event.customer()).isPresent();
        assertThat(event.customer().get().email()).isEqualTo("ada@example.com");
        assertThat(event.aggregateId()).isEqualTo(booking.id().value());
    }

    @Test(description = "A booking rebuilt from storage has no customer, so no second email goes out")
    public void confirmedEventHasNoCustomerWhenNoneWasAttached() {
        Booking booking = twoStaffBooking();
        booking.pullEvents();

        booking.confirm(APPOINTMENT);

        BookingConfirmed event = (BookingConfirmed) booking.pullEvents().get(0);
        assertThat(event.customer()).isEmpty();
    }
}
