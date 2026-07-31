package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.domain.model.AgencyMapping;
import com.booking.azure.domain.port.out.AgencyRepository;
import com.booking.azure.infrastructure.adapter.out.persistence.entity.AgencyJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AgencyPersistenceAdapter implements AgencyRepository {
    
    private final SpringDataAgencyRepository repository;

    @Override
    public Optional<AgencyMapping> findByFriendlyName(String friendlyName) {
        return repository.findByFriendlyName(friendlyName).map(this::mapToDomain);
    }

    private AgencyMapping mapToDomain(AgencyJpaEntity entity) {
        return AgencyMapping.builder()
                .id(entity.getId())
                .friendlyName(entity.getFriendlyName())
                .msTenantId(entity.getMsTenantId())
                .msBusinessId(entity.getMsBusinessId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}


