package com.booking.azure.domain.port.out;

import com.booking.azure.domain.model.AgencyMapping;
import java.util.Optional;

public interface AgencyRepository {
    Optional<AgencyMapping> findByFriendlyName(String friendlyName);
}


