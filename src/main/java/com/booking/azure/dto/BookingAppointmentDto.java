package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Repräsentiert einen Termin in Microsoft Bookings.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingAppointmentDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("serviceId")
    private String serviceId;

    @JsonProperty("serviceName")
    private String serviceName;

    @JsonProperty("serviceLocation")
    private LocationDto serviceLocation;

    @JsonProperty("serviceNotes")
    private String serviceNotes;

    @JsonProperty("startDateTime")
    private DateTimeTimeZoneDto startDateTime;

    @JsonProperty("endDateTime")
    private DateTimeTimeZoneDto endDateTime;

    @JsonProperty("duration")
    private String duration;

    @JsonProperty("staffMemberIds")
    private List<String> staffMemberIds;

    @JsonProperty("customers")
    private List<BookingCustomerInfoDto> customers;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("priceType")
    private String priceType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("isLocationOnline")
    private Boolean isLocationOnline;

    @JsonProperty("joinWebUrl")
    private String joinWebUrl;

    @JsonProperty("optOutOfCustomerEmail")
    private Boolean optOutOfCustomerEmail;

    @JsonProperty("anonymousJoinWebUrl")
    private String anonymousJoinWebUrl;

    @JsonProperty("additionalInformation")
    private String additionalInformation;

    @JsonProperty("createdDateTime")
    private String createdDateTime;

    @JsonProperty("lastUpdatedDateTime")
    private String lastUpdatedDateTime;
}
