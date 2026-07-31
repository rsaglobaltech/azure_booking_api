package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Repräsentiert einen Dienst einer Buchungsagentur in Microsoft Bookings.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingServiceDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("defaultDuration")
    private String defaultDuration;

    @JsonProperty("defaultPrice")
    private Double defaultPrice;

    @JsonProperty("defaultPriceType")
    private String defaultPriceType;

    @JsonProperty("defaultLocation")
    private LocationDto defaultLocation;

    @JsonProperty("isHiddenFromCustomers")
    private Boolean isHiddenFromCustomers;

    @JsonProperty("isLocationOnline")
    private Boolean isLocationOnline;

    @JsonProperty("staffMemberIds")
    private List<String> staffMemberIds;

    @JsonProperty("schedulingPolicy")
    private BookingSchedulingPolicyDto schedulingPolicy;

    @JsonProperty("createdDateTime")
    private String createdDateTime;

    @JsonProperty("lastUpdatedDateTime")
    private String lastUpdatedDateTime;
}


