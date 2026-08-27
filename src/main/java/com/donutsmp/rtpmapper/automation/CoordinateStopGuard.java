package com.donutsmp.rtpmapper.automation;

import java.util.Objects;

/** Evaluates opt-in stop zones against one delivered, stabilized RTP sample. */
public final class CoordinateStopGuard {
    public static final String OVERWORLD_DIMENSION = "minecraft:overworld";
    public static final double WORLD_BORDER_LIMIT_BLOCKS = 225_000.0;
    public static final double WORLD_CORNER_RADIUS_BLOCKS = Math.hypot(
            WORLD_BORDER_LIMIT_BLOCKS,
            WORLD_BORDER_LIMIT_BLOCKS
    );

    private CoordinateStopGuard() {
    }

    /**
     * Applies the guard only to a sample delivered by the immediately preceding
     * controller tick. The controller is stopped after the sample sink has
     * accepted the landing.
     */
    public static RtpStopReason stopAfterNewDelivery(
            RtpController controller,
            long deliveredBefore,
            RtpAttemptSettings completedAttemptSettings
    ) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(completedAttemptSettings, "completedAttemptSettings");
        if (!controller.isRunning() || controller.deliveredSamples() <= deliveredBefore) {
            return RtpStopReason.NONE;
        }

        RtpStopReason reason = controller.lastSampleResult()
                .map(sample -> evaluate(sample, completedAttemptSettings))
                .orElse(RtpStopReason.NONE);
        if (reason != RtpStopReason.NONE) {
            controller.stop(reason);
        }
        return reason;
    }

    public static RtpStopReason evaluate(RtpSampleResult sample, RtpAttemptSettings settings) {
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(settings, "settings");
        if (!OVERWORLD_DIMENSION.equals(sample.dimension())) {
            return RtpStopReason.NONE;
        }

        if (settings.stopNearCenter()
                && Math.hypot(sample.x(), sample.z()) <= settings.centerStopRadiusBlocks()) {
            return RtpStopReason.CENTER_GUARD_REACHED;
        }

        double nearestBorderDistance = WORLD_BORDER_LIMIT_BLOCKS
                - Math.max(Math.abs(sample.x()), Math.abs(sample.z()));
        if (settings.stopNearWorldBorder()
                && nearestBorderDistance <= settings.worldBorderMarginBlocks()) {
            return RtpStopReason.WORLD_BORDER_GUARD_REACHED;
        }
        return RtpStopReason.NONE;
    }
}
