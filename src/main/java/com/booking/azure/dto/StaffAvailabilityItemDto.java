package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Verfügbarkeitseintrag eines Mitarbeiters.
 * Gibt an, ob der Mitarbeiter Available, Busy usw. ist.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StaffAvailabilityItemDto {

    @JsonProperty("staffId")
    private String staffId;

    @JsonProperty("availabilityItems")
    private List<AvailabilityItemDto> availabilityItems;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AvailabilityItemDto {

        /**
         * Status des Mitarbeiters: Available, Busy, SlotsAvailable, OutOfOffice, usw.
         */
        @JsonProperty("status")
        private String status;

        @JsonProperty("startDateTime")
        private DateTimeTimeZoneDto startDateTime;

        @JsonProperty("endDateTime")
        private DateTimeTimeZoneDto endDateTime;

        /**
         * ID des Dienstes, wenn der Mitarbeiter gerade einen Dienst betreut.
         */
        @JsonProperty("serviceId")
        private String serviceId;
    }
}


