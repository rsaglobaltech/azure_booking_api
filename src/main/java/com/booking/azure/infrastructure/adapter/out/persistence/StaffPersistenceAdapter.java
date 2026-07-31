package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.domain.model.StaffMapping;
import com.booking.azure.domain.port.out.StaffRepository;
import com.booking.azure.infrastructure.adapter.out.persistence.entity.StaffJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StaffPersistenceAdapter implements StaffRepository {

    private final SpringDataStaffRepository repository;

    @Override
    public Optional<StaffMapping> findByAgencyIdAndFriendlyName(Long agencyId, String friendlyName) {
        return repository.findByAgencyIdAndFriendlyName(agencyId, friendlyName).map(this::mapToDomain);
    }

    private StaffMapping mapToDomain(StaffJpaEntity entity) {
        return StaffMapping.builder()
                .id(entity.getId())
                .friendlyName(entity.getFriendlyName())
                .msStaffMemberId(entity.getMsStaffMemberId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}


