package com.booking.azure.domain.model.vo;

import java.time.Duration;
import java.time.Instant;

/**
 * A half-open time interval {@code [start, end)}, always in UTC.
 *
 * <h2>Why this value object exists</h2>
 *
 * The overlap rule is the core invariant of this system: two bookings for the
 * same staff member must never overlap. Before this class existed the rule was
 * written three separate times — in the recovery service, in the SQL query and
 * in the reservation request — and could therefore drift apart. This class is
 * now the single source of truth; every other place delegates to it.
 *
 * <h2>Why the interval is half-open</h2>
 *
 * A booking ending at 11:00 and one starting at 11:00 do not overlap. Treating
 * the interval as closed would reject that pair and make back-to-back
 * appointments impossible.
 *
 * <h2>Why UTC</h2>
 *
 * Conversion from local time plus zone happens in the application layer, before
 * this object is built. Without that normalisation {@code 10:00 Europe/Berlin}
 * and {@code 08:00 UTC} would count as different windows and the collision
 * would go undetected.
 */
public record TimeWindow(Instant start, Instant end) {

    public TimeWindow {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end are required");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "end must be after start (start=%s, end=%s)".formatted(start, end));
        }
    }

    public static TimeWindow of(Instant start, Instant end) {
        return new TimeWindow(start, end);
    }

    /**
     * Whether this window overlaps another one.
     *
     * Half-open comparison: touching windows do not overlap.
     */
    public boolean overlaps(TimeWindow other) {
        return other.start.isBefore(this.end) && this.start.isBefore(other.end);
    }

    /** Whether {@code moment} falls inside this window (start inclusive, end exclusive). */
    public boolean contains(Instant moment) {
        return !moment.isBefore(start) && moment.isBefore(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    /**
     * Widens the window by {@code padding} on both sides.
     *
     * Used when querying Microsoft Bookings, which may add buffer time before
     * and after an appointment, so the returned times do not match the
     * requested ones exactly.
     */
    public TimeWindow paddedBy(Duration padding) {
        return new TimeWindow(start.minus(padding), end.plus(padding));
    }

    @Override
    public String toString() {
        return start + " – " + end;
    }
}
