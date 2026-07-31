package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingSchedulingPolicyDto {

    @JsonProperty("timeSlotInterval")
    private String timeSlotInterval;

    @JsonProperty("minimumLeadTime")
    private String minimumLeadTime;

    @JsonProperty("maximumAdvance")
    private String maximumAdvance;

    @JsonProperty("sendConfirmationsToOwner")
    private Boolean sendConfirmationsToOwner;

    @JsonProperty("allowStaffSelection")
    private Boolean allowStaffSelection;
}


