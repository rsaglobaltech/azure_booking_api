package com.booking.azure.infrastructure.adapter.in.web.dto.request;

import com.booking.azure.dto.BookingWorkHoursDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Anfrage zum Erstellen oder Aktualisieren eines Mitarbeiters.
 */
@Data
public class CreateStaffMemberRequest {

    @NotBlank(message = "displayName es obligatorio")
    @JsonProperty("displayName")
    private String displayName;

    @NotBlank(message = "emailAddress es obligatorio")
    @JsonProperty("emailAddress")
    private String emailAddress;

    /**
     * Roles: guest, administrator, viewer, externalGuest, scheduler, teamMember
     */
    @NotBlank(message = "role es obligatorio")
    @JsonProperty("role")
    private String role;

    @JsonProperty("timeZone")
    private String timeZone;

    @JsonProperty("useBusinessHours")
    private Boolean useBusinessHours;

    @JsonProperty("availabilityIsAffectedByPersonalCalendar")
    private Boolean availabilityIsAffectedByPersonalCalendar;

    @JsonProperty("isEmailNotificationEnabled")
    private Boolean isEmailNotificationEnabled;

    @JsonProperty("workingHours")
    private List<BookingWorkHoursDto> workingHours;
}


