package com.donutsmp.rtpmapper.data;

import com.donutsmp.rtpmapper.region.RtpRegion;

import java.util.Objects;

/** A single confirmed, settled RTP result. Coordinates retain double precision. */
public record RtpSample(
        long sampleNumber,
        double x,
        double y,
        double z,
        String dimension,
        long timestamp,
        RtpRegion requestedRegion,
        SampleCategory category
) {
    public static final double MISSING_Y = Double.NaN;

    public RtpSample {
        if (sampleNumber < 1) {
            throw new IllegalArgumentException("sampleNumber must be positive");
        }
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("X and Z coordinates must be finite");
        }
        if (!Double.isFinite(y) && !Double.isNaN(y)) {
            throw new IllegalArgumentException("Y must be finite or MISSING_Y");
        }
        dimension = Objects.requireNonNull(dimension, "dimension").trim();
        if (dimension.isEmpty()) {
            throw new IllegalArgumentException("dimension cannot be blank");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp cannot be negative");
        }
        requestedRegion = Objects.requireNonNullElse(requestedRegion, RtpRegion.UNKNOWN);
        category = Objects.requireNonNullElse(category, SampleCategory.DEFAULT);
    }

    public RtpSample(
            long sampleNumber,
            double x,
            double y,
            double z,
            String dimension,
            long timestamp,
            SampleCategory category
    ) {
        this(sampleNumber, x, y, z, dimension, timestamp, RtpRegion.UNKNOWN, category);
    }

    public RtpSample(
            long sampleNumber,
            double x,
            double y,
            double z,
            String dimension,
            long timestamp,
            RtpRegion requestedRegion
    ) {
        this(sampleNumber, x, y, z, dimension, timestamp, requestedRegion, SampleCategory.DEFAULT);
    }

    public RtpSample(
            long sampleNumber,
            double x,
            double y,
            double z,
            String dimension,
            long timestamp
    ) {
        this(sampleNumber, x, y, z, dimension, timestamp, RtpRegion.UNKNOWN, SampleCategory.DEFAULT);
    }

    public long sample() {
        return sampleNumber;
    }

    public boolean hasY() {
        return Double.isFinite(y);
    }

    public double distanceFromOrigin() {
        return Math.hypot(x, z);
    }
}
