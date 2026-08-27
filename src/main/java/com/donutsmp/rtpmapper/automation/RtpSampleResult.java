package com.donutsmp.rtpmapper.automation;

import com.donutsmp.rtpmapper.region.RtpRegion;

import java.util.Objects;

/**
 * Framework-neutral result of one confirmed, stabilized request. The data
 * layer is responsible for assigning the persistent all-time sample number.
 */
public record RtpSampleResult(
        long requestNumber,
        double x,
        double y,
        double z,
        String dimension,
        long timestampMillis,
        double horizontalDistanceFromBaseline,
        boolean storeYCoordinate,
        RtpRegion requestedRegion
) {
    public RtpSampleResult {
        if (requestNumber <= 0) {
            throw new IllegalArgumentException("requestNumber must be positive");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Sample coordinates must be finite");
        }
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        if (!Double.isFinite(horizontalDistanceFromBaseline) || horizontalDistanceFromBaseline < 0) {
            throw new IllegalArgumentException("horizontalDistanceFromBaseline must be finite and non-negative");
        }
        Objects.requireNonNull(requestedRegion, "requestedRegion");
    }

    /**
     * Compatibility constructor for results created before region selection
     * was introduced.
     */
    public RtpSampleResult(
            long requestNumber,
            double x,
            double y,
            double z,
            String dimension,
            long timestampMillis,
            double horizontalDistanceFromBaseline,
            boolean storeYCoordinate
    ) {
        this(
                requestNumber,
                x,
                y,
                z,
                dimension,
                timestampMillis,
                horizontalDistanceFromBaseline,
                storeYCoordinate,
                RtpRegion.UNKNOWN
        );
    }
}
