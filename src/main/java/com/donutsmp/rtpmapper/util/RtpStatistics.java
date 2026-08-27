package com.donutsmp.rtpmapper.util;

import com.donutsmp.rtpmapper.data.SampleCategory;
import com.donutsmp.rtpmapper.data.SampleScope;
import com.donutsmp.rtpmapper.region.RtpRegion;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable aggregate statistics. Empty numeric aggregates are represented by NaN. */
public record RtpStatistics(
        SampleScope scope,
        long sourceRevision,
        long totalSamples,
        double averageX,
        double averageZ,
        double minimumX,
        double maximumX,
        double minimumZ,
        double maximumZ,
        double averageDistance,
        double minimumDistance,
        double maximumDistance,
        List<RadialBucket> radialBuckets,
        Map<Quadrant, QuadrantStatistics> quadrantStatistics,
        Map<SampleCategory, Long> categoryCounts,
        Map<RtpRegion, Long> regionCounts,
        Map<String, Long> dimensionCounts
) {
    public RtpStatistics {
        scope = Objects.requireNonNull(scope, "scope");
        if (sourceRevision < -1 || totalSamples < 0) {
            throw new IllegalArgumentException("Invalid statistics revision/count");
        }
        radialBuckets = List.copyOf(radialBuckets);
        quadrantStatistics = Map.copyOf(quadrantStatistics);
        categoryCounts = Map.copyOf(categoryCounts);
        EnumMap<RtpRegion, Long> orderedRegionCounts = new EnumMap<>(RtpRegion.class);
        orderedRegionCounts.putAll(Objects.requireNonNull(regionCounts, "regionCounts"));
        regionCounts = Collections.unmodifiableMap(orderedRegionCounts);
        dimensionCounts = Map.copyOf(dimensionCounts);
    }

    public boolean hasSamples() {
        return totalSamples > 0;
    }

    public QuadrantStatistics quadrant(Quadrant quadrant) {
        return quadrantStatistics.get(quadrant);
    }
}
