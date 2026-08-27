package com.donutsmp.rtpmapper.util;

import java.util.Locale;

/** A half-open radial interval: {@code [minimumDistance, maximumDistance)}. */
public record RadialBucket(double minimumDistance, double maximumDistance, long count) {
    public RadialBucket {
        if (!Double.isFinite(minimumDistance) || !Double.isFinite(maximumDistance)
                || minimumDistance < 0.0 || maximumDistance <= minimumDistance || count < 0) {
            throw new IllegalArgumentException("Invalid radial bucket");
        }
    }

    public String label() {
        return compact(minimumDistance) + "-" + compact(maximumDistance);
    }

    private static String compact(double value) {
        if (value >= 1_000_000.0 && value % 1_000_000.0 == 0.0) {
            return String.format(Locale.ROOT, "%.0fm", value / 1_000_000.0);
        }
        if (value >= 1_000.0 && value % 1_000.0 == 0.0) {
            return String.format(Locale.ROOT, "%.0fk", value / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
