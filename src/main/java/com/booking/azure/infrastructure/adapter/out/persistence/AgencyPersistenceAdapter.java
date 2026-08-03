package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.domain.model.Agency;
import com.booking.azure.domain.model.StaffMember;
import com.booking.azure.domain.model.vo.AgencyId;
import com.booking.azure.domain.model.vo.AgencyName;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.StaffName;
import com.booking.azure.domain.model.vo.TenantId;
import com.booking.azure.domain.port.out.AgencyRepository;
import com.booking.azure.infrastructure.adapter.out.persistence.entity.AgencyJpaEntity;
import com.booking.azure.infrastructure.adapter.out.persistence.entity.StaffJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Infrastructure adapter: rebuilds the {@link Agency} aggregate from its JPA rows.
 *
 * The JPA entities never leave this class. Everything above works with the
 * aggregate, so a change to the table layout stops here instead of rippling
 * into the domain.
 */
@Component
@RequiredArgsConstructor
public class AgencyPersistenceAdapter implements AgencyRepository {

    private final SpringDataAgencyRepository repository;

    @Override
    public Optional<Agency> findByName(AgencyName name) {
        return repository.findByFriendlyNameWithStaff(name.value()).map(this::toDomain);
    }

    @Override
    public Optional<Agency> findByBusinessId(BusinessId businessId) {
        return repository.findByMsBusinessIdWithStaff(businessId.value()).map(this::toDomain);
    }

    @Override
    public List<Agency> findAll() {
        return repository.findAllWithStaff().stream().map(this::toDomain).toList();
    }

    private Agency toDomain(AgencyJpaEntity entity) {
        List<StaffMember> staff = entity.getStaffMappings() == null
                ? List.of()
                : entity.getStaffMappings().stream().map(this::toDomain).toList();

        return new Agency(
                AgencyId.of(entity.getId()),
                AgencyName.of(entity.getFriendlyName()),
                TenantId.of(entity.getMsTenantId()),
                BusinessId.of(entity.getMsBusinessId()),
                staff);
    }

    private StaffMember toDomain(StaffJpaEntity entity) {
        return new StaffMember(
                StaffMemberId.of(entity.getMsStaffMemberId()),
                StaffName.of(entity.getFriendlyName()));
    }
}
