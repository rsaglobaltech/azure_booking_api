package com.booking.azure.domain.model;

import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class AgencyMapping {
    private Long id;
    private String friendlyName;
    private String msTenantId;
    private String msBusinessId;
    private List<StaffMapping> staffMappings;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}


