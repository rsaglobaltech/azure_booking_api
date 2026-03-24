package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Repräsentiert einen Mitarbeiter in Microsoft Bookings.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingStaffMemberDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("emailAddress")
    private String emailAddress;

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

    @JsonProperty("membershipStatus")
    private String membershipStatus;

    @JsonProperty("workingHours")
    private List<BookingWorkHoursDto> workingHours;

    @JsonProperty("createdDateTime")
    private String createdDateTime;

    @JsonProperty("lastUpdatedDateTime")
    private String lastUpdatedDateTime;
}
