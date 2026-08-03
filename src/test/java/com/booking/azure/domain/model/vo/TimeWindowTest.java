package com.booking.azure.domain.model.vo;

import org.testng.annotations.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The overlap rule, which is the core invariant of the whole system.
 *
 * These cases used to be implicit in three separate implementations — the
 * recovery service, the SQL query and the reservation request — with nothing
 * pinning them to the same meaning.
 */
public class TimeWindowTest {

    private static final Instant TEN = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-03-01T11:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-03-01T12:00:00Z");

    @Test(description = "Windows that merely touch do not overlap, so back-to-back appointments are bookable")
    public void touchingWindowsDoNotOverlap() {
        TimeWindow first = TimeWindow.of(TEN, ELEVEN);
        TimeWindow second = TimeWindow.of(ELEVEN, TWELVE);

        assertThat(first.overlaps(second)).isFalse();
        assertThat(second.overlaps(first)).isFalse();
    }

    @Test(description = "Partially overlapping windows collide, in both directions")
    public void partialOverlapIsDetectedSymmetrically() {
        TimeWindow first = TimeWindow.of(TEN, TWELVE);
        TimeWindow second = TimeWindow.of(ELEVEN, TWELVE.plus(Duration.ofHours(1)));

        assertThat(first.overlaps(second)).isTrue();
        assertThat(second.overlaps(first)).isTrue();
    }

    @Test(description = "A window fully containing another collides with it")
    public void containmentIsAnOverlap() {
        TimeWindow outer = TimeWindow.of(TEN, TWELVE);
        TimeWindow inner = TimeWindow.of(TEN.plus(Duration.ofMinutes(10)),
                                         TEN.plus(Duration.ofMinutes(20)));

        assertThat(outer.overlaps(inner)).isTrue();
        assertThat(inner.overlaps(outer)).isTrue();
    }

    @Test(description = "A window overlaps itself — a booking always collides with its own slot")
    public void windowOverlapsItself() {
        TimeWindow window = TimeWindow.of(TEN, ELEVEN);
        assertThat(window.overlaps(window)).isTrue();
    }

    @Test(description = "contains() is start-inclusive and end-exclusive, matching the overlap rule")
    public void containsIsHalfOpen() {
        TimeWindow window = TimeWindow.of(TEN, ELEVEN);

        assertThat(window.contains(TEN)).isTrue();
        assertThat(window.contains(TEN.plus(Duration.ofMinutes(30)))).isTrue();
        assertThat(window.contains(ELEVEN)).isFalse();
    }

    @Test(description = "Padding widens the window on both sides, for the Bookings buffer-time case")
    public void paddingWidensBothSides() {
        TimeWindow padded = TimeWindow.of(TEN, ELEVEN).paddedBy(Duration.ofHours(1));

        assertThat(padded.start()).isEqualTo(TEN.minus(Duration.ofHours(1)));
        assertThat(padded.end()).isEqualTo(ELEVEN.plus(Duration.ofHours(1)));
    }

    @Test(description = "A window must have positive duration")
    public void rejectsNonPositiveDuration() {
        assertThatThrownBy(() -> TimeWindow.of(ELEVEN, TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end must be after start");

        assertThatThrownBy(() -> TimeWindow.of(TEN, TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test(description = "Both bounds are required")
    public void rejectsMissingBounds() {
        assertThatThrownBy(() -> TimeWindow.of(null, ELEVEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeWindow.of(TEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
