package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Anfrage zur Abfrage der Verfügbarkeit von Mitarbeitern.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StaffAvailabilityRequestDto {

    @JsonProperty("staffIds")
    private List<String> staffIds;

    @JsonProperty("startDateTime")
    private DateTimeTimeZoneDto startDateTime;

    @JsonProperty("endDateTime")
    private DateTimeTimeZoneDto endDateTime;
}
