package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.infrastructure.adapter.out.persistence.entity.AgencyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataAgencyRepository extends JpaRepository<AgencyJpaEntity, Long> {

    /**
     * Loads the agency and its staff in a single query.
     *
     * The {@code left join fetch} is required, not an optimisation: the
     * {@code @OneToMany} is lazy and the mapping to the domain aggregate happens
     * outside any transaction, so a lazy collection would fail with
     * {@code LazyInitializationException} instead of returning the staff.
     * {@code left} rather than inner, so an agency with no staff still loads.
     */
    @Query("select a from AgencyJpaEntity a left join fetch a.staffMappings "
            + "where a.friendlyName = :friendlyName")
    Optional<AgencyJpaEntity> findByFriendlyNameWithStaff(@Param("friendlyName") String friendlyName);

    /** Same fetch-join reasoning as above, keyed by the Microsoft business id. */
    @Query("select a from AgencyJpaEntity a left join fetch a.staffMappings "
            + "where a.msBusinessId = :msBusinessId")
    Optional<AgencyJpaEntity> findByMsBusinessIdWithStaff(@Param("msBusinessId") String msBusinessId);
}
