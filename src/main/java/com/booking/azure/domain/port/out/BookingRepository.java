package com.booking.azure.domain.port.out;

import com.booking.azure.domain.exception.SlotConflictException;
import com.booking.azure.domain.model.Booking;
import com.booking.azure.domain.model.OrphanedReservation;
import com.booking.azure.domain.model.SlotRequest;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BookingId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for storing and retrieving the {@link Booking} aggregate.
 *
 * Domain layer: defines the contract; the implementation
 * ({@code BookingJpaAdapter}) lives in the infrastructure layer.
 *
 * <h2>Por qué la exclusión mutua vive detrás de este puerto</h2>
 *
 * Microsoft Graph no puede ser la autoridad: sobre {@code bookingAppointment} no
 * ofrece bloqueos, ni transacciones, ni escrituras condicionales, y el endpoint
 * que usamos es el administrativo, que permite el sobrecupo a propósito. La
 * regla de que dos reservas no se solapen vive por tanto aquí, aplicada dentro
 * de la transacción que escribe las reservas.
 *
 * <h2>Orden de llamada previsto</h2>
 *
 * <pre>
 *   1. reserve(...)                          → transacción propia, se confirma ya
 *   2. POST a Microsoft Graph                → fuera de toda transacción
 *   3a. booking.confirm(id); save(booking)   → si Graph acepta
 *   3b. booking.release();  save(booking)    → si Graph rechaza (compensación)
 * </pre>
 *
 * <p><b>El paso 2 no debe ejecutarse dentro de una transacción abierta.</b>
 * Mantener una transacción abierta a lo largo de una llamada de red agota el
 * pool de conexiones bajo carga: un problema de corrección se convertiría en una
 * caída del servicio.
 *
 * <h2>El tercer caso, el peligroso</h2>
 *
 * Si Graph <b>no responde</b> (timeout, conexión cortada), no se compensa: la
 * reserva se queda en {@code PENDING} a propósito. Un timeout del cliente no
 * aborta el trabajo del servidor — el {@code POST} puede haber creado la cita
 * igualmente. Liberar el hueco ahí es exactamente lo que produce la doble
 * reserva. Véase {@code SlotRecoveryService}.
 */
public interface BookingRepository {

    /**
     * Creates and persists a booking holding the window for every requested
     * staff member — all or nothing.
     *
     * @param request window in UTC, business, service and staff members
     * @return the persisted booking, its reservations carrying their assigned ids
     * @throws SlotConflictException if the window overlaps a blocking
     *         reservation for at least one staff member
     */
    Booking reserve(SlotRequest request);

    /**
     * Writes the aggregate's current state back to storage.
     *
     * Used after {@link Booking#confirm} and {@link Booking#release}. The
     * transitions themselves are the aggregate's business, not this port's.
     */
    void save(Booking booking);

    Optional<Booking> findById(BookingId bookingId);

    /**
     * Loads the booking linked to a Graph appointment, if it still holds slots.
     *
     * @return empty if no blocking booking references that appointment — which
     *         is the normal case for an already-cancelled appointment
     */
    Optional<Booking> findBlockingByAppointmentId(AppointmentId appointmentId);

    /**
     * Moves an appointment to a new window: releases the old booking and takes
     * the new one, in <b>one</b> transaction.
     *
     * Two separate calls would be wrong: in between, another request could claim
     * the old slot, and if taking the new window then failed, the old one would
     * already be lost.
     *
     * @return the new booking, already linked to the same appointment
     * @throws SlotConflictException if the new window is taken
     */
    Booking reschedule(AppointmentId appointmentId, SlotRequest newWindow);

    /**
     * Finds reservations that are still {@code PENDING} past their deadline.
     *
     * <p><b>These must not be released blindly.</b> A deadline passing does not
     * abort an in-flight HTTP call and does not prove the appointment is
     * missing. Releasing without checking Graph first produces exactly the
     * double booking the mechanism prevents.
     *
     * @param before cut-off; reservations expiring earlier than this are returned
     */
    List<OrphanedReservation> findOrphaned(Instant before);
}
