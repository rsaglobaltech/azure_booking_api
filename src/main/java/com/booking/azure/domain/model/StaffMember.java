package com.booking.azure.domain.model;

import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.StaffName;

/**
 * A staff member of an agency, able to be assigned to appointments.
 *
 * Entity, not value object: two staff members with the same name are still
 * different people, so identity is carried by {@link #staffMemberId} rather
 * than by the field values.
 *
 * <p>Belongs to the {@link Agency} aggregate and is never loaded or modified on
 * its own — every access goes through the root.
 */
public class StaffMember {

    private final StaffMemberId staffMemberId;
    private final StaffName name;

    public StaffMember(StaffMemberId staffMemberId, StaffName name) {
        if (staffMemberId == null) {
            throw new IllegalArgumentException("staffMemberId is required");
        }
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        this.staffMemberId = staffMemberId;
        this.name = name;
    }

    public StaffMemberId staffMemberId() {
        return staffMemberId;
    }

    public StaffName name() {
        return name;
    }

    /** Whether this staff member is the one callers mean by {@code candidate}. */
    public boolean isNamed(StaffName candidate) {
        return name.equals(candidate);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StaffMember that)) return false;
        return staffMemberId.equals(that.staffMemberId);
    }

    @Override
    public int hashCode() {
        return staffMemberId.hashCode();
    }

    @Override
    public String toString() {
        return "%s (%s)".formatted(name, staffMemberId);
    }
}
