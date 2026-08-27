package com.donutsmp.rtpmapper.automation;

import java.util.Objects;

/** Pure coordinate and dimension predicates used by the controller. */
public final class TeleportDetector {
    public boolean isTeleport(
            PositionObservation baseline,
            PositionObservation current,
            double horizontalThresholdBlocks
    ) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(current, "current");
        if (!Double.isFinite(horizontalThresholdBlocks) || horizontalThresholdBlocks <= 0) {
            throw new IllegalArgumentException("horizontalThresholdBlocks must be finite and positive");
        }

        return !baseline.dimension().equals(current.dimension())
                || horizontalDistance(baseline, current) >= horizontalThresholdBlocks;
    }

    public boolean isStable(
            PositionObservation previous,
            PositionObservation current,
            double toleranceBlocks
    ) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (!Double.isFinite(toleranceBlocks) || toleranceBlocks < 0) {
            throw new IllegalArgumentException("toleranceBlocks must be finite and non-negative");
        }
        if (!previous.dimension().equals(current.dimension())) {
            return false;
        }

        double horizontal = horizontalDistance(previous, current);
        return Math.hypot(horizontal, current.y() - previous.y()) <= toleranceBlocks;
    }

    public double horizontalDistance(PositionObservation first, PositionObservation second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return Math.hypot(second.x() - first.x(), second.z() - first.z());
    }
}
