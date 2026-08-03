package com.booking.azure.domain.model;

import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;

import java.time.Instant;

/**
 * One staff member's hold on one time window.
 *
 * Entity inside the {@link Booking} aggregate: it has its own identity (the
 * database row id) but no life of its own. It is never loaded, changed or saved
 * except through the root, which is what keeps the all-or-nothing rule
 * enforceable — a reservation that could be released on its own would leave a
 * booking holding some of its slots and not others.
 *
 * <h2>Where the state machine used to live</h2>
 *
 * These transitions were previously performed by the JPA adapter calling
 * {@code setState(...)} directly on a persistence entity. Nothing stopped a
 * released row from being confirmed again. The rules now sit next to the data
 * they constrain.
 */
public class SlotReservation {

    /** {@code null} until the row has been written. */
    private Long id;

    private final StaffMemberId staffMemberId;
    private final TimeWindow window;
    private final Instant expiresAt;

    private SlotStatus status;
    private AppointmentId appointmentId;

    private SlotReservation(Long id,
                            StaffMemberId staffMemberId,
                            TimeWindow window,
                            Instant expiresAt,
                            SlotStatus status,
                            AppointmentId appointmentId) {
        if (staffMemberId == null) {
            throw new IllegalArgumentException("staffMemberId is required");
        }
        if (window == null) {
            throw new IllegalArgumentException("window is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
        this.id = id;
        this.staffMemberId = staffMemberId;
        this.window = window;
        this.expiresAt = expiresAt;
        this.status = status;
        this.appointmentId = appointmentId;
    }

    /** A fresh hold, not yet written and not yet known to Graph. */
    static SlotReservation pending(StaffMemberId staffMemberId, TimeWindow window, Instant expiresAt) {
        return new SlotReservation(null, staffMemberId, window, expiresAt, SlotStatus.PENDING, null);
    }

    /** Rebuilds a reservation from storage, state included. */
    public static SlotReservation rehydrate(Long id,
                                            StaffMemberId staffMemberId,
                                            TimeWindow window,
                                            Instant expiresAt,
                                            SlotStatus status,
                                            AppointmentId appointmentId) {
        if (id == null) {
            throw new IllegalArgumentException("id is required when rehydrating");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required when rehydrating");
        }
        return new SlotReservation(id, staffMemberId, window, expiresAt, status, appointmentId);
    }

    public Long id() {
        return id;
    }

    public StaffMemberId staffMemberId() {
        return staffMemberId;
    }

    public TimeWindow window() {
        return window;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public SlotStatus status() {
        return status;
    }

    public AppointmentId appointmentId() {
        return appointmentId;
    }

    /** Whether this reservation still blocks the slot for other bookings. */
    public boolean isBlocking() {
        return status == SlotStatus.PENDING || status == SlotStatus.CONFIRMED;
    }

    /**
     * Whether the deadline has passed while still {@code PENDING}.
     *
     * <p>Expiry is <b>not</b> permission to release. It only marks the row as
     * worth checking against Graph — see {@code SlotRecoveryService}.
     */
    public boolean isOrphaned(Instant now) {
        return status == SlotStatus.PENDING && expiresAt.isBefore(now);
    }

    public boolean overlaps(TimeWindow other) {
        return window.overlaps(other);
    }

    // ─────────────────────────── package-private transitions ───────────────────
    // Only Booking may drive these; that is what keeps the all-or-nothing rule
    // enforceable.

    void confirm(AppointmentId appointmentId) {
        this.status = SlotStatus.CONFIRMED;
        this.appointmentId = appointmentId;
    }

    void release() {
        this.status = SlotStatus.RELEASED;
    }

    /** Records the identity assigned by the database on first write. */
    public void assignId(Long id) {
        if (this.id != null && !this.id.equals(id)) {
            throw new IllegalStateException("reservation id already assigned: " + this.id);
        }
        this.id = id;
    }

    @Override
    public String toString() {
        return "%s %s [%s]".formatted(staffMemberId, window, status);
    }
}
