package com.booking.azure.infrastructure.adapter.out.graph.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Generischer Wrapper für Listen-Antworten der Microsoft Graph API (OData).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphListResponse<T> {

    @JsonProperty("@odata.context")
    private String odataContext;

    @JsonProperty("@odata.nextLink")
    private String odataNextLink;

    @JsonProperty("value")
    private List<T> value;
}


