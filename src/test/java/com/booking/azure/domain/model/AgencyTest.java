package com.booking.azure.domain.model;

import com.booking.azure.domain.exception.StaffMemberNotFoundException;
import com.booking.azure.domain.model.vo.AgencyId;
import com.booking.azure.domain.model.vo.AgencyName;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.StaffName;
import com.booking.azure.domain.model.vo.TenantId;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Name-to-identifier translation, which used to be a loop in the appointment
 * use case that raised an HTTP-flavoured error.
 */
public class AgencyTest {

    private Agency agency() {
        return new Agency(
                AgencyId.of(1L),
                AgencyName.of("Downtown"),
                TenantId.of("tenant-1"),
                BusinessId.of("business-1"),
                List.of(
                        new StaffMember(StaffMemberId.of("id-anna"), StaffName.of("Anna")),
                        new StaffMember(StaffMemberId.of("id-bruno"), StaffName.of("Bruno"))));
    }

    @Test(description = "A known name resolves to the Microsoft identifier")
    public void resolvesAKnownStaffName() {
        assertThat(agency().resolveStaff(StaffName.of("Anna")))
                .isEqualTo(StaffMemberId.of("id-anna"));
    }

    @Test(description = "Several names resolve in the order they were given")
    public void resolvesSeveralNamesInOrder() {
        List<StaffMemberId> resolved = agency()
                .resolveStaff(List.of(StaffName.of("Bruno"), StaffName.of("Anna")));

        assertThat(resolved).containsExactly(
                StaffMemberId.of("id-bruno"), StaffMemberId.of("id-anna"));
    }

    @Test(description = "An unknown name fails loudly rather than yielding a shorter list, "
            + "which would reserve fewer slots than requested")
    public void unknownStaffNameFailsTheWholeResolution() {
        assertThatThrownBy(() -> agency()
                .resolveStaff(List.of(StaffName.of("Anna"), StaffName.of("Nobody"))))
                .isInstanceOf(StaffMemberNotFoundException.class)
                .hasMessageContaining("Nobody")
                .hasMessageContaining("Downtown");
    }

    @Test(description = "No names requested means no staff resolved, not an error")
    public void emptyRequestResolvesToEmpty() {
        assertThat(agency().resolveStaff(List.of())).isEmpty();
    }

    @Test(description = "An agency without staff cannot resolve anything")
    public void agencyWithoutStaffResolvesNothing() {
        Agency empty = new Agency(AgencyId.of(2L), AgencyName.of("Empty"), TenantId.of("tenant-1"),
                BusinessId.of("business-2"), List.of());

        assertThatThrownBy(() -> empty.resolveStaff(StaffName.of("Anna")))
                .isInstanceOf(StaffMemberNotFoundException.class);
    }
}
