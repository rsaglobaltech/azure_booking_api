package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationDto {

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("address")
    private PhysicalAddressDto address;
}
