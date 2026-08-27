package com.donutsmp.rtpmapper.automation;

import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinateStopGuardTest {
    @Test
    void centerGuardIsInclusiveAndUsesRadialDistance() {
        RtpAttemptSettings enabled = settings(true, 50_000, false, 10_000);

        assertEquals(RtpStopReason.CENTER_GUARD_REACHED,
                CoordinateStopGuard.evaluate(sample(30_000, 40_000), enabled));
        assertEquals(RtpStopReason.NONE,
                CoordinateStopGuard.evaluate(sample(30_001, 40_000), enabled));
    }

    @Test
    void borderGuardUsesDistanceToSquareFacesNotRadius() {
        RtpAttemptSettings enabled = settings(false, 50_000, true, 10_000);

        assertEquals(RtpStopReason.WORLD_BORDER_GUARD_REACHED,
                CoordinateStopGuard.evaluate(sample(215_000, 0), enabled));
        assertEquals(RtpStopReason.WORLD_BORDER_GUARD_REACHED,
                CoordinateStopGuard.evaluate(sample(0, -215_000), enabled));
        assertEquals(RtpStopReason.NONE,
                CoordinateStopGuard.evaluate(sample(214_999, 214_999), enabled));
        assertEquals(RtpStopReason.NONE,
                CoordinateStopGuard.evaluate(sample(160_000, 160_000), enabled));
    }

    @Test
    void guardsAreOptInAndOverworldOnly() {
        RtpAttemptSettings disabled = settings(false, 50_000, false, 10_000);
        RtpAttemptSettings enabled = settings(true, 50_000, true, 10_000);

        assertEquals(RtpStopReason.NONE,
                CoordinateStopGuard.evaluate(sample(0, 0), disabled));
        assertEquals(RtpStopReason.NONE,
                CoordinateStopGuard.evaluate(sample(224_999, 0), disabled));
        assertEquals(RtpStopReason.NONE,
                CoordinateStopGuard.evaluate(netherSample(0, 0), enabled));
        assertEquals(RtpStopReason.NONE,
                CoordinateStopGuard.evaluate(netherSample(28_000, 0), enabled));
    }

    @Test
    void centerGuardHasDefinedPrecedenceWhenZonesOverlap() {
        RtpAttemptSettings overlapping = settings(true, 318_000, true, 225_000);

        assertEquals(RtpStopReason.CENTER_GUARD_REACHED,
                CoordinateStopGuard.evaluate(sample(1_000, 1_000), overlapping));
    }

    private static RtpAttemptSettings settings(
            boolean stopNearCenter,
            double centerRadius,
            boolean stopNearWorldBorder,
            double borderMargin
    ) {
        return new RtpAttemptSettings(
                Duration.ofSeconds(5).toNanos(),
                512,
                Duration.ofSeconds(20).toNanos(),
                1,
                1,
                Duration.ofSeconds(5).toNanos(),
                0.25,
                true,
                RtpRegion.NA_EAST,
                stopNearCenter,
                centerRadius,
                stopNearWorldBorder,
                borderMargin
        );
    }

    private static RtpSampleResult sample(double x, double z) {
        return sample(x, z, "minecraft:overworld");
    }

    private static RtpSampleResult netherSample(double x, double z) {
        return sample(x, z, "minecraft:the_nether");
    }

    private static RtpSampleResult sample(double x, double z, String dimension) {
        return new RtpSampleResult(
                1, x, 64, z, dimension, 1, 1_000, true, RtpRegion.NA_EAST
        );
    }
}
