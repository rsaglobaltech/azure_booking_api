package com.booking.azure.domain.model;

import com.booking.azure.domain.exception.StaffMemberNotFoundException;
import com.booking.azure.domain.model.vo.AgencyId;
import com.booking.azure.domain.model.vo.AgencyName;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.StaffName;

import java.util.ArrayList;
import java.util.List;

/**
 * A booking agency: aggregate root over its staff members.
 *
 * <h2>What this aggregate is for</h2>
 *
 * It owns the translation between the names callers use and the identifiers
 * Microsoft Bookings expects. That translation used to be a loop inside the
 * appointment use case, which meant the use case had to know how staff are
 * looked up and had to raise an HTTP-flavoured error when one was missing.
 * Both concerns now live here, where the data they operate on lives.
 *
 * <h2>Consistency boundary</h2>
 *
 * The staff list is loaded with the root and is never modified from outside.
 * Slot reservations are deliberately <b>not</b> part of this aggregate: they
 * change on every booking, while an agency's staff changes rarely, and tying
 * the two together would force the whole staff list to be loaded and locked for
 * every booking.
 */
public class Agency {

    private final AgencyId id;
    private final AgencyName name;
    private final String msTenantId;
    private final BusinessId businessId;
    private final List<StaffMember> staff;

    public Agency(AgencyId id,
                  AgencyName name,
                  String msTenantId,
                  BusinessId businessId,
                  List<StaffMember> staff) {
        if (id == null) {
            throw new IllegalArgumentException("agency id is required");
        }
        if (name == null) {
            throw new IllegalArgumentException("agency name is required");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("businessId is required");
        }
        this.id = id;
        this.name = name;
        this.msTenantId = msTenantId;
        this.businessId = businessId;
        this.staff = staff == null ? List.of() : List.copyOf(staff);
    }

    public AgencyId id() {
        return id;
    }

    public AgencyName name() {
        return name;
    }

    public String msTenantId() {
        return msTenantId;
    }

    /** The identifier this agency carries inside Microsoft Bookings. */
    public BusinessId businessId() {
        return businessId;
    }

    public List<StaffMember> staff() {
        return staff;
    }

    /**
     * Translates a caller-facing staff name into the Microsoft identifier.
     *
     * @throws StaffMemberNotFoundException if this agency has nobody by that name
     */
    public StaffMemberId resolveStaff(StaffName staffName) {
        return staff.stream()
                .filter(member -> member.isNamed(staffName))
                .findFirst()
                .map(StaffMember::staffMemberId)
                .orElseThrow(() -> new StaffMemberNotFoundException(name, staffName));
    }

    /**
     * Translates several staff names at once, preserving their order.
     *
     * Fails on the first unknown name rather than silently returning a shorter
     * list: a booking assigned to fewer people than requested would reserve
     * fewer slots than it should.
     *
     * @throws StaffMemberNotFoundException if any name is unknown to this agency
     */
    public List<StaffMemberId> resolveStaff(List<StaffName> staffNames) {
        if (staffNames == null || staffNames.isEmpty()) {
            return List.of();
        }
        List<StaffMemberId> resolved = new ArrayList<>(staffNames.size());
        for (StaffName staffName : staffNames) {
            resolved.add(resolveStaff(staffName));
        }
        return List.copyOf(resolved);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Agency that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "%s (%s)".formatted(name, businessId);
    }
}
