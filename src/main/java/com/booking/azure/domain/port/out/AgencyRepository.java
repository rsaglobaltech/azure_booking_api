package com.booking.azure.domain.port.out;

import com.booking.azure.domain.model.Agency;
import com.booking.azure.domain.model.vo.AgencyName;
import com.booking.azure.domain.model.vo.BusinessId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for loading the {@link Agency} aggregate.
 *
 * The aggregate always comes back complete, staff members included. There is
 * deliberately no port for loading a staff member on its own: doing so would
 * let callers work with a fragment of the aggregate and bypass the root, which
 * is the only place that knows how names map onto Microsoft identifiers.
 */
public interface AgencyRepository {

    /**
     * Loads an agency together with its staff members.
     *
     * @param name the caller-facing agency name
     * @return the aggregate, or empty if no agency carries that name
     */
    Optional<Agency> findByName(AgencyName name);

    /**
     * Loads an agency by the identifier it carries inside Microsoft Bookings.
     *
     * Needed by subscribers that only see what a domain event carries: events
     * reference the business id, not the local display name.
     */
    Optional<Agency> findByBusinessId(BusinessId businessId);

    /**
     * Every agency this platform knows about.
     *
     * This is the authoritative answer to "which agencies exist", and the only
     * one available across tenants: agencies live in different Entra ID
     * directories, so no single call to Microsoft can enumerate them.
     */
    List<Agency> findAll();
}
