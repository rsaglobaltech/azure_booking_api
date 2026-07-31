package com.booking.azure.domain.model;

import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@Builder
public class BookingDetails {
    private String customerName;
    private String customerEmail;
    private String agencyName;
    private String agencyEmail;
    private String workerName;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String serviceName;
}


