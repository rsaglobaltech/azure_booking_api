package com.booking.azure.domain.port.out;

import com.booking.azure.domain.model.StaffMapping;
import java.util.Optional;

public interface StaffRepository {
    Optional<StaffMapping> findByAgencyIdAndFriendlyName(Long agencyId, String friendlyName);
}


