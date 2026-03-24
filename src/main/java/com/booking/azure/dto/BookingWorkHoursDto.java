package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingWorkHoursDto {

    @JsonProperty("day")
    private String day;

    @JsonProperty("timeSlots")
    private List<BookingWorkTimeSlotDto> timeSlots;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookingWorkTimeSlotDto {

        @JsonProperty("startTime")
        private String startTime;

        @JsonProperty("endTime")
        private String endTime;
    }
}
