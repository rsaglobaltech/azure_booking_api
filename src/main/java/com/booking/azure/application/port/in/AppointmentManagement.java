package com.booking.azure.application.port.in;

import com.booking.azure.application.command.CreateAppointmentRequest;
import com.booking.azure.dto.BookingAppointmentDto;

import java.util.List;

/**
 * Inbound port (use-case interface) for appointment management.
 *
 * Domain layer: this port has no dependency on outer layers. The presentation
 * layer (controllers) calls only this interface; the implementation lives in the
 * application layer.
 *
 * Booking URL pattern: https://outlook.office.com/book/{agencyName}@domain.com
 */
public interface AppointmentManagement {

    /**
     * Lists every appointment of a booking business (agency).
     *
     * @param agencyName name of the agency
     * @return all appointments of that agency
     */
    List<BookingAppointmentDto> listAppointments(String agencyName);

    /**
     * Retrieves the appointments falling in a date range (calendar view).
     *
     * @param agencyName    name of the agency
     * @param startDateTime start of the range, ISO-8601 (e.g. 2024-06-01T00:00:00Z)
     * @param endDateTime   end of the range, ISO-8601
     * @return the appointments inside the range
     */
    List<BookingAppointmentDto> getCalendarView(String agencyName,
            String startDateTime,
            String endDateTime);

    /**
     * Retrieves a single appointment.
     *
     * @param agencyName    name of the agency
     * @param appointmentId id (GUID) of the appointment
     * @return the appointment
     */
    BookingAppointmentDto getAppointment(String agencyName, String appointmentId);

    /**
     * Creates a new appointment and assigns it to a staff member.
     *
     * @param agencyName name of the agency
     * @param request    service, time and customer details
     * @return the created appointment
     */
    BookingAppointmentDto createAppointment(String agencyName, CreateAppointmentRequest request);

    /**
     * Updates an existing appointment.
     *
     * @param agencyName    name of the agency
     * @param appointmentId id of the appointment being updated
     * @param request       the new details
     * @return the updated appointment
     */
    BookingAppointmentDto updateAppointment(String agencyName,
            String appointmentId,
            CreateAppointmentRequest request);

    /**
     * Cancels (deletes) an appointment.
     *
     * @param agencyName    name of the agency
     * @param appointmentId id of the appointment being cancelled
     */
    void cancelAppointment(String agencyName, String appointmentId);
}
