package com.booking.azure.domain.exception;

import com.booking.azure.domain.model.vo.AgencyName;

/** No agency is registered under the given name. */
public class AgencyNotFoundException extends DomainException {

    public AgencyNotFoundException(AgencyName name) {
        super("Agency not found: " + name);
    }
}
