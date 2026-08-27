package com.donutsmp.rtpmapper.automation;

import com.donutsmp.rtpmapper.region.RtpRegion;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings captured once for a single command attempt. Later configuration
 * changes therefore cannot alter a request already in flight.
 */
public record RtpAttemptSettings(
        long cooldownNanos,
        double teleportThresholdBlocks,
        long teleportTimeoutNanos,
        int minimumStabilizationTicks,
        int requiredStableTicks,
        long maximumStabilizationNanos,
        double stabilityToleranceBlocks,
        boolean storeYCoordinate,
        RtpRegion requestedRegion,
        boolean stopNearCenter,
        double centerStopRadiusBlocks,
        boolean stopNearWorldBorder,
        double worldBorderMarginBlocks
) {
    public RtpAttemptSettings {
        if (cooldownNanos <= 0) {
            throw new IllegalArgumentException("cooldownNanos must be positive");
        }
        if (!Double.isFinite(teleportThresholdBlocks) || teleportThresholdBlocks <= 0) {
            throw new IllegalArgumentException("teleportThresholdBlocks must be finite and positive");
        }
        if (teleportTimeoutNanos <= 0) {
            throw new IllegalArgumentException("teleportTimeoutNanos must be positive");
        }
        if (minimumStabilizationTicks < 0) {
            throw new IllegalArgumentException("minimumStabilizationTicks must not be negative");
        }
        if (requiredStableTicks <= 0) {
            throw new IllegalArgumentException("requiredStableTicks must be positive");
        }
        if (maximumStabilizationNanos <= 0) {
            throw new IllegalArgumentException("maximumStabilizationNanos must be positive");
        }
        if (!Double.isFinite(stabilityToleranceBlocks) || stabilityToleranceBlocks < 0) {
            throw new IllegalArgumentException("stabilityToleranceBlocks must be finite and non-negative");
        }
        Objects.requireNonNull(requestedRegion, "requestedRegion");
        if (!requestedRegion.selectable()) {
            throw new IllegalArgumentException("requestedRegion must be a selectable RTP region");
        }
        requireRange("centerStopRadiusBlocks", centerStopRadiusBlocks,
                0.0, CoordinateStopGuard.WORLD_CORNER_RADIUS_BLOCKS);
        requireRange("worldBorderMarginBlocks", worldBorderMarginBlocks,
                0.0, CoordinateStopGuard.WORLD_BORDER_LIMIT_BLOCKS);
    }

    /** Compatibility constructor for callers that predate coordinate stop guards. */
    public RtpAttemptSettings(
            long cooldownNanos,
            double teleportThresholdBlocks,
            long teleportTimeoutNanos,
            int minimumStabilizationTicks,
            int requiredStableTicks,
            long maximumStabilizationNanos,
            double stabilityToleranceBlocks,
            boolean storeYCoordinate,
            RtpRegion requestedRegion
    ) {
        this(
                cooldownNanos,
                teleportThresholdBlocks,
                teleportTimeoutNanos,
                minimumStabilizationTicks,
                requiredStableTicks,
                maximumStabilizationNanos,
                stabilityToleranceBlocks,
                storeYCoordinate,
                requestedRegion,
                false,
                50_000.0,
                false,
                10_000.0
        );
    }

    /**
     * Compatibility constructor for callers that predate region selection.
     * New code should always pass the region explicitly.
     */
    public RtpAttemptSettings(
            long cooldownNanos,
            double teleportThresholdBlocks,
            long teleportTimeoutNanos,
            int minimumStabilizationTicks,
            int requiredStableTicks,
            long maximumStabilizationNanos,
            double stabilityToleranceBlocks,
            boolean storeYCoordinate
    ) {
        this(
                cooldownNanos,
                teleportThresholdBlocks,
                teleportTimeoutNanos,
                minimumStabilizationTicks,
                requiredStableTicks,
                maximumStabilizationNanos,
                stabilityToleranceBlocks,
                storeYCoordinate,
                RtpRegion.NA_EAST
        );
    }

    public static RtpAttemptSettings defaults() {
        return new RtpAttemptSettings(
                Duration.ofSeconds(5).toNanos(),
                512.0,
                Duration.ofSeconds(20).toNanos(),
                15,
                5,
                Duration.ofSeconds(5).toNanos(),
                0.25,
                true,
                RtpRegion.NA_EAST
        );
    }

    private static void requireRange(String name, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
    }
}
