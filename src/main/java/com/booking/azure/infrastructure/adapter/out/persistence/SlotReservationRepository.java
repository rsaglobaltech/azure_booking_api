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
     * La comprobación de solape del algoritmo de colisión.
     *
     * <h2>Cómo se lee la condición</h2>
     *
     * <pre>
     * s.startUtc &lt; :endUtc  AND  s.endUtc &gt; :startUtc
     * </pre>
     *
     * Es la comparación de <b>intervalo semiabierto</b> {@code [inicio, fin)}, y
     * coincide exactamente con {@code TimeWindow.overlaps}. Dos citas que
     * simplemente se tocan —una acaba a las 11:00 y la siguiente empieza a las
     * 11:00— <b>no</b> colisionan. Tratar el intervalo como cerrado haría
     * imposible encadenar citas seguidas.
     *
     * <h2>Por qué solo cuentan PENDING y CONFIRMED</h2>
     *
     * Son los dos estados que retienen el hueco. {@code RELEASED} deja la fila
     * como rastro de auditoría pero ya no bloquea nada, así que queda fuera del
     * recuento.
     *
     * <h2>Las horas están en UTC</h2>
     *
     * Y tienen que estarlo. {@code 10:00 Europe/Madrid} y {@code 08:00 UTC} son
     * el mismo instante; si se compararan tal como llegaron, esta consulta no
     * vería la colisión y dos clientes se llevarían el mismo hueco. La
     * normalización ocurre en {@code TimeWindow}, antes de llegar aquí.
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
