package com.donutsmp.rtpmapper.automation;

import java.util.Objects;

/** A finite player position observed on the client thread. */
public record PositionObservation(double x, double y, double z, String dimension) {
    public PositionObservation {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Player coordinates must be finite");
        }
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
    }
}
