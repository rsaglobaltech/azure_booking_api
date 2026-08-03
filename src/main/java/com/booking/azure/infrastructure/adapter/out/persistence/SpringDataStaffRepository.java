package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.infrastructure.adapter.out.persistence.entity.StaffJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface SpringDataStaffRepository extends JpaRepository<StaffJpaEntity, Long> {

    /**
     * Serialises concurrent booking requests for the same staff member.
     *
     * Looking staff up by name is deliberately absent: that lookup belongs to
     * the {@code Agency} aggregate, which loads its staff with the root. This
     * repository exists only for the lock that guards the overlap check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StaffJpaEntity s WHERE s.msStaffMemberId = :msStaffMemberId")
    Optional<StaffJpaEntity> lockByMsStaffMemberId(@Param("msStaffMemberId") String msStaffMemberId);
}


