package com.booking.azure.application.port.out;

import com.booking.azure.domain.model.AppointmentDraft;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.dto.BookingAppointmentDto;

/**
 * Outbound port for writing appointments to the external calendar.
 *
 * <h2>Why this sits next to the generic Graph port rather than replacing it</h2>
 *
 * {@link GraphApiRequest} is shaped like HTTP — paths and verbs — which is fine
 * for the administrative CRUD around businesses, services and staff, where this
 * system is a thin pass-through. Appointments are different: they are the part
 * the domain actually reasons about, so the writes go through an intention
 * ({@link AppointmentDraft}) and the adapter decides what JSON that becomes.
 *
 * <h2>Why reads are not here</h2>
 *
 * Queries return {@link BookingAppointmentDto} straight from Graph, untouched.
 * Routing them through domain types would mean remodelling every field Graph
 * returns, and anything left unmodelled would silently vanish from this API's
 * responses. Commands go through the model; queries do not need it.
 */
public interface AppointmentCalendarPort {

    /**
     * Creates the appointment.
     *
     * @return the created appointment as the calendar reports it, which is also
     *         what this API returns to its caller
     */
    BookingAppointmentDto create(BusinessId businessId, AppointmentDraft draft);

    /** Rewrites an existing appointment with the draft's contents. */
    BookingAppointmentDto update(BusinessId businessId, AppointmentId appointmentId,
                                 AppointmentDraft draft);

    /** Removes the appointment from the calendar. */
    void cancel(BusinessId businessId, AppointmentId appointmentId);
}
