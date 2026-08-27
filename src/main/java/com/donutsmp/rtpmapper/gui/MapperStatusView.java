package com.donutsmp.rtpmapper.gui;

import com.donutsmp.rtpmapper.region.RtpRegion;

public record MapperStatusView(
    boolean running,
    boolean serverAllowed,
    String state,
    int sessionSamples,
    int allTimeSamples,
    boolean hasCurrentPosition,
    double currentX,
    double currentY,
    double currentZ,
    boolean hasLastSample,
    long lastSampleNumber,
    double lastX,
    double lastY,
    double lastZ,
    String lastDimension,
    long lastTimestamp,
    double secondsUntilNextAction,
    long sessionDurationMillis,
    int failedAttempts,
    String detail,
    RtpRegion targetRegion,
    RtpRegion lastRequestedRegion,
    int selectedRegionCount
) {
    public MapperStatusView {
        state = state == null ? "IDLE" : state;
        lastDimension = lastDimension == null ? "" : lastDimension;
        detail = detail == null ? "" : detail;
        targetRegion = targetRegion == null ? RtpRegion.UNKNOWN : targetRegion;
        lastRequestedRegion = lastRequestedRegion == null ? RtpRegion.UNKNOWN : lastRequestedRegion;
        if (selectedRegionCount < 1) {
            throw new IllegalArgumentException("selectedRegionCount must be positive");
        }
    }
}
