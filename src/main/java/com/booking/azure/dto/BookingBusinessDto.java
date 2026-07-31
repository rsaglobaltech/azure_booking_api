package com.booking.azure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO für einen Microsoft-Bookings-Betrieb (Agentur).
 *
 * Das Feld {@code buchungsUrl} wird serverseitig dynamisch berechnet:
 *   <pre>https://outlook.office.com/book/{agenturName}@midominio.com</pre>
 *
 * Der Agenturname ergibt sich immer aus dem {@code id}-Feld und ist niemals statisch.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingBusinessDto {

    /** Eindeutige ID der Agentur (z. B. agenturfreiburg@midominio.com) */
    @JsonProperty("id")
    private String id;

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

    @JsonProperty("isPublished")
    private Boolean isPublished;

    /** Öffentliche Buchungs-URL von Microsoft Graph */
    @JsonProperty("publicUrl")
    private String publicUrl;

    /**
     * Dynamisch berechnete Buchungs-URL:
     *   https://outlook.office.com/book/{agenturName}@midominio.com
     * Wird vom BookingBusinessService gesetzt. Immer dynamisch.
     */
    private String buchungsUrl;

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

    @JsonProperty("createdDateTime")
    private String createdDateTime;

    @JsonProperty("lastUpdatedDateTime")
    private String lastUpdatedDateTime;
}


