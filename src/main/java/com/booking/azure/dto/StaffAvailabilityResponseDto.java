package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Antwort der Graph-API für Mitarbeiterverfügbarkeit.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StaffAvailabilityResponseDto {

    @JsonProperty("staffAvailabilityItem")
    private List<StaffAvailabilityItemDto> staffAvailabilityItem;
}


