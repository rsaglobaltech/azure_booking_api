package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Kundeninformationen für einen Termin.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingCustomerInfoDto {

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("emailAddress")
    private String emailAddress;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("notes")
    private String notes;
}
