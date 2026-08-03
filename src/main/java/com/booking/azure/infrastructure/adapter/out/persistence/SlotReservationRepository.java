package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.domain.model.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for slot reservation rows.
 *
 * Infrastructure layer. Rows are grouped into {@code Booking} aggregates by
 * {@code BookingJpaAdapter}; nothing above that class sees this interface.
 */
public interface SlotReservationRepository extends JpaRepository<SlotReservationEntity, Long> {

    /**
     * The overlap check behind the collision algorithm.
     *
     * Half-open comparison ({@code start < :endUtc AND end > :startUtc}), matching
     * {@code TimeWindow.overlaps} exactly: appointments that merely touch do not
     * collide.
     */
    @Query("SELECT COUNT(s) FROM SlotReservationEntity s " +
           "WHERE s.businessId = :businessId " +
           "AND s.staffMemberId = :staffMemberId " +
           "AND s.state IN ('PENDING', 'CONFIRMED') " +
           "AND s.startUtc < :endUtc AND s.endUtc > :startUtc")
    int countOverlappingReservations(
            @Param("businessId") String businessId,
            @Param("staffMemberId") String staffMemberId,
            @Param("startUtc") Instant startUtc,
            @Param("endUtc") Instant endUtc);

    /** Every row of one booking aggregate. */
    List<SlotReservationEntity> findByBookingId(String bookingId);

    /** Active reservations for a Graph appointment (cancellation and reschedule). */
    List<SlotReservationEntity> findByGraphAppointmentIdAndStateIn(
            String graphAppointmentId, Collection<SlotStatus> states);

    /**
     * Orphaned reservations for the recovery job.
     *
     * <p><b>Careful:</b> these rows must not be released blindly. A row expiring
     * does not abort an in-flight HTTP call — a blind release produces exactly
     * the double booking it is meant to prevent. Check
     * {@code GET /calendarView} first to see whether the appointment came into
     * existence after all. See docs/PLAN-COLISION-RESERVAS.md §2.5.
     */
    List<SlotReservationEntity> findByStateAndExpiresAtBefore(SlotStatus state, Instant moment);
}
