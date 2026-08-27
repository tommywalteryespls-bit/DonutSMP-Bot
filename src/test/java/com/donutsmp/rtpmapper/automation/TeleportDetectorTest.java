package com.donutsmp.rtpmapper.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportDetectorTest {
    private final TeleportDetector detector = new TeleportDetector();

    @Test
    void horizontalDistanceAtThresholdCountsAsTeleportAndYDoesNot() {
        PositionObservation baseline = position(0, 64, 0, "minecraft:overworld");

        assertTrue(detector.isTeleport(
                baseline,
                position(600, 5_000, 800, "minecraft:overworld"),
                1_000
        ));
        assertFalse(detector.isTeleport(
                baseline,
                position(0, 5_000, 0, "minecraft:overworld"),
                1_000
        ));
    }

    @Test
    void dimensionChangeCountsAsTeleportAtSameCoordinates() {
        PositionObservation baseline = position(100, 64, -200, "minecraft:overworld");
        PositionObservation nether = position(100, 64, -200, "minecraft:the_nether");

        assertTrue(detector.isTeleport(baseline, nether, 50_000));
    }

    @Test
    void stabilityUsesThreeDimensionalMovementAndRequiresSameDimension() {
        PositionObservation first = position(10, 70, 10, "minecraft:overworld");

        assertTrue(detector.isStable(
                first,
                position(10.1, 70.1, 10.1, "minecraft:overworld"),
                0.2
        ));
        assertFalse(detector.isStable(
                first,
                position(10.1, 70.3, 10.1, "minecraft:overworld"),
                0.2
        ));
        assertFalse(detector.isStable(
                first,
                position(10, 70, 10, "minecraft:the_nether"),
                1
        ));
    }

    private static PositionObservation position(double x, double y, double z, String dimension) {
        return new PositionObservation(x, y, z, dimension);
    }
}
