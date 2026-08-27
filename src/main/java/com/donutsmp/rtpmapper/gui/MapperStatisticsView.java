package com.donutsmp.rtpmapper.gui;

import com.donutsmp.rtpmapper.region.RtpRegion;
import java.util.List;

public record MapperStatisticsView(
    int totalSamples,
    double averageX,
    double averageZ,
    double minimumX,
    double maximumX,
    double minimumZ,
    double maximumZ,
    double averageDistance,
    double minimumDistance,
    double maximumDistance,
    double northEastPercent,
    double northWestPercent,
    double southEastPercent,
    double southWestPercent,
    List<RadialBucketView> radialBuckets,
    List<RegionCountView> requestedRegionCounts
) {
    public MapperStatisticsView {
        radialBuckets = radialBuckets == null ? List.of() : List.copyOf(radialBuckets);
        requestedRegionCounts = requestedRegionCounts == null ? List.of() : List.copyOf(requestedRegionCounts);
    }

    public static MapperStatisticsView empty() {
        List<RegionCountView> regions = RtpRegion.displayValues().stream()
            .map(region -> new RegionCountView(region, 0))
            .toList();
        return new MapperStatisticsView(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            List.of(), regions
        );
    }

    public record RadialBucketView(double minimumInclusive, double maximumExclusive, int count) {
    }

    public record RegionCountView(RtpRegion region, int count) {
        public RegionCountView {
            region = region == null ? RtpRegion.UNKNOWN : region;
            if (count < 0) {
                throw new IllegalArgumentException("count cannot be negative");
            }
        }
    }
}
