package com.booking.azure.domain.model;

import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@Builder
public class StaffMapping {
    private Long id;
    private AgencyMapping agency;
    private String friendlyName;
    private String msStaffMemberId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}


