package com.donutsmp.rtpmapper.util;

public record QuadrantStatistics(long count, double percentage) {
    public QuadrantStatistics {
        if (count < 0 || !Double.isFinite(percentage) || percentage < 0.0 || percentage > 100.0) {
            throw new IllegalArgumentException("Invalid quadrant statistics");
        }
    }
}
