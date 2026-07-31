package com.booking.azure.infrastructure.adapter.in.web.dto.request;

import com.booking.azure.dto.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Anfrage zum Erstellen oder Aktualisieren einer Buchungsagentur.
 */
@Data
public class CreateBookingBusinessRequest {

    @NotBlank(message = "displayName es obligatorio")
    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("businessType")
    private String businessType;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("webSiteUrl")
    private String webSiteUrl;

    @JsonProperty("defaultCurrencyIso")
    private String defaultCurrencyIso;

    @JsonProperty("languageTag")
    private String languageTag;

    @JsonProperty("address")
    private PhysicalAddressDto address;

    @JsonProperty("businessHours")
    private List<BookingWorkHoursDto> businessHours;

    @JsonProperty("schedulingPolicy")
    private BookingSchedulingPolicyDto schedulingPolicy;
}


