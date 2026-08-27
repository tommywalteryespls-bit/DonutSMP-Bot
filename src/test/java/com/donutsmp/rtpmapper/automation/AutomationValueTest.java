package com.donutsmp.rtpmapper.automation;

import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutomationValueTest {
    @Test
    void rejectsNonFinitePositions() {
        assertThrows(IllegalArgumentException.class,
                () -> new PositionObservation(Double.NaN, 64, 0, "minecraft:overworld"));
        assertThrows(IllegalArgumentException.class,
                () -> new PositionObservation(0, 64, Double.POSITIVE_INFINITY, "minecraft:overworld"));
    }

    @Test
    void rejectsUnsafeAttemptSettings() {
        long second = Duration.ofSeconds(1).toNanos();
        assertThrows(IllegalArgumentException.class,
                () -> new RtpAttemptSettings(0, 1_000, second, 10, 5, second, 0.25, true));
        assertThrows(IllegalArgumentException.class,
                () -> new RtpAttemptSettings(second, Double.NaN, second, 10, 5, second, 0.25, true));
        assertThrows(IllegalArgumentException.class,
                () -> new RtpAttemptSettings(second, 1_000, second, -1, 5, second, 0.25, true));
        assertThrows(IllegalArgumentException.class,
                () -> new RtpAttemptSettings(second, 1_000, second, 10, 0, second, 0.25, true));
        assertThrows(IllegalArgumentException.class,
                () -> new RtpAttemptSettings(second, 1_000, second, 10, 5, second, -0.1, true));
        assertThrows(NullPointerException.class,
                () -> new RtpAttemptSettings(second, 1_000, second, 10, 5, second, 0.25, true, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RtpAttemptSettings(
                        second, 1_000, second, 10, 5, second, 0.25, true, RtpRegion.UNKNOWN
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new RtpAttemptSettings(
                        second, 1_000, second, 10, 5, second, 0.25, true, RtpRegion.NA_EAST,
                        true, Double.NaN, false, 10_000
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new RtpAttemptSettings(
                        second, 1_000, second, 10, 5, second, 0.25, true, RtpRegion.NA_EAST,
                        false, 50_000, true, CoordinateStopGuard.WORLD_BORDER_LIMIT_BLOCKS + 1
                ));
    }

    @Test
    void legacyRoutingDefaultsToNaEastButMissingSampleProvenanceStaysUnknown() {
        long second = Duration.ofSeconds(1).toNanos();
        RtpAttemptSettings settings = new RtpAttemptSettings(
                second, 1_000, second, 10, 5, second, 0.25, true
        );
        RtpSampleResult result = new RtpSampleResult(
                1, 1, 64, 2, "minecraft:overworld", 123, 10, true
        );

        assertEquals(RtpRegion.NA_EAST, settings.requestedRegion());
        assertEquals(RtpRegion.UNKNOWN, result.requestedRegion());
        assertThrows(NullPointerException.class,
                () -> new RtpSampleResult(
                        1, 1, 64, 2, "minecraft:overworld", 123, 10, true, null
                ));
    }
}
