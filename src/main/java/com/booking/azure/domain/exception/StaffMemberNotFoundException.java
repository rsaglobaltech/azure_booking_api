package com.booking.azure.domain.exception;

import com.booking.azure.domain.model.vo.AgencyName;
import com.booking.azure.domain.model.vo.StaffName;

/** The agency has no staff member registered under the given name. */
public class StaffMemberNotFoundException extends DomainException {

    public StaffMemberNotFoundException(AgencyName agency, StaffName staff) {
        super("Staff member not found in agency %s: %s".formatted(agency, staff));
    }
}
