package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.domain.model.Booking;
import com.booking.azure.domain.model.SlotReservation;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BookingId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Translates between the {@link Booking} aggregate and its database rows.
 *
 * The JPA entity is now a plain row mapping with no behaviour: every state
 * transition happens on the aggregate, and this class only writes the result
 * down. That separation is what keeps a released reservation from being
 * silently re-confirmed by a stray {@code setState} call.
 */
@Component
public class BookingMapper {

    /**
     * Rebuilds one aggregate from the rows sharing its booking id.
     *
     * @throws IllegalArgumentException if handed an empty set — a booking with
     *         no reservations is not a state this system can represent
     */
    public Booking toDomain(List<SlotReservationEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("cannot rebuild a booking from zero rows");
        }

        SlotReservationEntity first = rows.get(0);

        List<SlotReservation> reservations = rows.stream()
                .map(this::toDomain)
                .toList();

        return Booking.rehydrate(
                BookingId.of(first.getBookingId()),
                BusinessId.of(first.getBusinessId()),
                ServiceId.of(first.getServiceId()),
                TimeWindow.of(first.getStartUtc(), first.getEndUtc()),
                reservations,
                first.getGraphAppointmentId() == null
                        ? null
                        : AppointmentId.of(first.getGraphAppointmentId()));
    }

    private SlotReservation toDomain(SlotReservationEntity row) {
        return SlotReservation.rehydrate(
                row.getId(),
                StaffMemberId.of(row.getStaffMemberId()),
                TimeWindow.of(row.getStartUtc(), row.getEndUtc()),
                row.getExpiresAt(),
                row.getState(),
                row.getGraphAppointmentId() == null
                        ? null
                        : AppointmentId.of(row.getGraphAppointmentId()));
    }

    /** Builds a fresh row for a reservation that has never been written. */
    public SlotReservationEntity toNewEntity(Booking booking, SlotReservation reservation, Instant now) {
        SlotReservationEntity row = new SlotReservationEntity();
        row.setBookingId(booking.id().value());
        row.setBusinessId(booking.businessId().value());
        row.setServiceId(booking.serviceId().value());
        row.setStaffMemberId(reservation.staffMemberId().value());
        row.setStartUtc(reservation.window().start());
        row.setEndUtc(reservation.window().end());
        row.setState(reservation.status());
        // Taken from the root, not the reservation: a rescheduled booking already
        // knows its appointment while its reservations are still PENDING.
        row.setGraphAppointmentId(
                booking.appointmentId() == null ? null : booking.appointmentId().value());
        row.setExpiresAt(reservation.expiresAt());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    /** Copies the aggregate's current state onto an existing row. */
    public void applyState(Booking booking, SlotReservation reservation,
                           SlotReservationEntity row, Instant now) {
        row.setState(reservation.status());
        row.setGraphAppointmentId(
                booking.appointmentId() == null ? null : booking.appointmentId().value());
        row.setUpdatedAt(now);
    }
}
