package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.infrastructure.adapter.out.persistence.entity.AgencyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataAgencyRepository extends JpaRepository<AgencyJpaEntity, Long> {
    Optional<AgencyJpaEntity> findByFriendlyName(String friendlyName);
}


