package com.booking.azure.domain.command;

import com.booking.azure.dto.BookingCustomerInfoDto;
import com.booking.azure.dto.DateTimeTimeZoneDto;
import com.booking.azure.dto.LocationDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Anfrage zum Erstellen eines Termins.
 */
@Data
public class CreateAppointmentRequest {

    @NotBlank(message = "serviceId es obligatorio")
    @JsonProperty("serviceId")
    private String serviceId;

    @NotNull(message = "startDateTime es obligatorio")
    @Valid
    @JsonProperty("startDateTime")
    private DateTimeTimeZoneDto startDateTime;

    @NotNull(message = "endDateTime es obligatorio")
    @Valid
    @JsonProperty("endDateTime")
    private DateTimeTimeZoneDto endDateTime;

    @JsonProperty("workerNames")
    private List<String> workerNames;

    @JsonProperty("staffMemberIds")
    private List<String> staffMemberIds;

    @JsonProperty("customers")
    private List<BookingCustomerInfoDto> customers;

    @JsonProperty("serviceNotes")
    private String serviceNotes;

    @JsonProperty("additionalInformation")
    private String additionalInformation;

    @JsonProperty("serviceLocation")
    private LocationDto serviceLocation;

    @JsonProperty("isLocationOnline")
    private Boolean isLocationOnline;

    @JsonProperty("optOutOfCustomerEmail")
    private Boolean optOutOfCustomerEmail;
}


