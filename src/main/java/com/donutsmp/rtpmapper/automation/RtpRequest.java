package com.donutsmp.rtpmapper.automation;

import java.util.Objects;

/** Immutable public view of the single pending command request. */
public record RtpRequest(
        long requestNumber,
        PositionObservation baseline,
        RtpAttemptSettings settings,
        long sentAtNanos,
        long teleportDeadlineNanos
) {
    public RtpRequest {
        if (requestNumber <= 0) {
            throw new IllegalArgumentException("requestNumber must be positive");
        }
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(settings, "settings");
    }
}
