package com.booking.azure.domain.model;

import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;

import java.util.List;

/**
 * Request to reserve a time window for one or more staff members.
 *
 * Domain layer: a pure value object, with no dependency on JPA, Spring or
 * Jackson.
 *
 * <p>The window is a {@link TimeWindow} and therefore always UTC and always
 * validated. Conversion from local time plus zone happens in the application
 * layer, before this object is built.
 *
 * @param businessId     the booking business the slot belongs to
 * @param serviceId      the service being booked
 * @param staffMemberIds every assigned staff member; one reservation row is
 *                       created per member. If any one of them is busy, the
 *                       whole reservation fails.
 * @param window         the requested window, UTC, half-open
 */
public record SlotRequest(
        BusinessId businessId,
        ServiceId serviceId,
        List<StaffMemberId> staffMemberIds,
        TimeWindow window) {

    public SlotRequest {
        if (businessId == null) {
            throw new IllegalArgumentException("businessId is required");
        }
        if (window == null) {
            throw new IllegalArgumentException("window is required");
        }
        if (staffMemberIds == null || staffMemberIds.isEmpty()) {
            throw new IllegalArgumentException("at least one staff member is required");
        }
        staffMemberIds = List.copyOf(staffMemberIds);
    }
}
