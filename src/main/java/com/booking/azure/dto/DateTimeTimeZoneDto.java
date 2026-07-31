package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DateTimeTimeZoneDto {

    @JsonProperty("dateTime")
    private String dateTime;

    @JsonProperty("timeZone")
    private String timeZone;
}


