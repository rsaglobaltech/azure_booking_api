package com.booking.azure.dto.request;

import com.booking.azure.dto.BookingSchedulingPolicyDto;
import com.booking.azure.dto.LocationDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Anfrage zum Erstellen oder Aktualisieren eines Buchungsdienstes.
 */
@Data
public class CreateServiceRequest {

    @NotBlank(message = "displayName es obligatorio")
    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("defaultDuration")
    private String defaultDuration;

    @JsonProperty("defaultPrice")
    private Double defaultPrice;

    /**
     * Tipos: notSet, fixedPrice, startingAt, hourly, free, priceVaries, callUs, notDisplayed
     */
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
}
