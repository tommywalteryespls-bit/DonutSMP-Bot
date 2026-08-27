package com.donutsmp.rtpmapper.util;

import com.donutsmp.rtpmapper.data.RtpDatasetSnapshot;
import com.donutsmp.rtpmapper.data.RtpSample;
import com.donutsmp.rtpmapper.data.SampleCategory;
import com.donutsmp.rtpmapper.data.SampleScope;
import com.donutsmp.rtpmapper.region.RtpRegion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Computes and caches all requested coordinate, radial, and quadrant aggregates. */
public final class StatisticsCalculator {
    public static final double DEFAULT_RADIAL_BUCKET_SIZE = 25_000.0;

    private final double radialBucketSize;
    private CacheEntry allTimeCache;
    private CacheEntry sessionCache;

    public StatisticsCalculator() {
        this(DEFAULT_RADIAL_BUCKET_SIZE);
    }

    public StatisticsCalculator(double radialBucketSize) {
        if (!Double.isFinite(radialBucketSize) || radialBucketSize <= 0.0) {
            throw new IllegalArgumentException("radialBucketSize must be finite and positive");
        }
        this.radialBucketSize = radialBucketSize;
    }

    public synchronized RtpStatistics calculate(RtpDatasetSnapshot snapshot, SampleScope scope) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(scope, "scope");
        List<RtpSample> samples = snapshot.samples(scope);
        long sourceRevision = snapshot.revision(scope);
        CacheEntry cache = scope == SampleScope.SESSION ? sessionCache : allTimeCache;
        if (cache != null && cache.sourceRevision == sourceRevision && cache.samples == samples) {
            return cache.statistics;
        }

        RtpStatistics statistics = computeInternal(samples, scope, sourceRevision, radialBucketSize);
        CacheEntry replacement = new CacheEntry(samples, sourceRevision, statistics);
        if (scope == SampleScope.SESSION) {
            sessionCache = replacement;
        } else {
            allTimeCache = replacement;
        }
        return statistics;
    }

    public synchronized void invalidate() {
        allTimeCache = null;
        sessionCache = null;
    }

    public static RtpStatistics compute(Collection<RtpSample> samples, SampleScope scope) {
        return compute(samples, scope, DEFAULT_RADIAL_BUCKET_SIZE);
    }

    public static RtpStatistics compute(
            Collection<RtpSample> samples,
            SampleScope scope,
            double radialBucketSize
    ) {
        Objects.requireNonNull(samples, "samples");
        if (!Double.isFinite(radialBucketSize) || radialBucketSize <= 0.0) {
            throw new IllegalArgumentException("radialBucketSize must be finite and positive");
        }
        return computeInternal(List.copyOf(samples), scope, -1, radialBucketSize);
    }

    public double radialBucketSize() {
        return radialBucketSize;
    }

    private static RtpStatistics computeInternal(
            List<RtpSample> samples,
            SampleScope scope,
            long sourceRevision,
            double bucketSize
    ) {
        EnumMap<Quadrant, Long> quadrantCounts = new EnumMap<>(Quadrant.class);
        for (Quadrant quadrant : Quadrant.values()) {
            quadrantCounts.put(quadrant, 0L);
        }
        EnumMap<SampleCategory, Long> categoryCounts = new EnumMap<>(SampleCategory.class);
        for (SampleCategory category : SampleCategory.values()) {
            categoryCounts.put(category, 0L);
        }
        EnumMap<RtpRegion, Long> regionCounts = new EnumMap<>(RtpRegion.class);
        for (RtpRegion region : RtpRegion.displayValues()) {
            regionCounts.put(region, 0L);
        }
        Map<String, Long> dimensionCounts = new LinkedHashMap<>();

        if (samples.isEmpty()) {
            return new RtpStatistics(
                    scope, sourceRevision, 0,
                    Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN,
                    List.of(), percentages(quadrantCounts, 0), categoryCounts, regionCounts, dimensionCounts
            );
        }

        KahanSum xSum = new KahanSum();
        KahanSum zSum = new KahanSum();
        KahanSum distanceSum = new KahanSum();
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        double minimumDistance = Double.POSITIVE_INFINITY;
        double maximumDistance = Double.NEGATIVE_INFINITY;
        Map<Integer, Long> sparseBuckets = new LinkedHashMap<>();

        for (RtpSample sample : samples) {
            xSum.add(sample.x());
            zSum.add(sample.z());
            double distance = sample.distanceFromOrigin();
            distanceSum.add(distance);
            minimumX = Math.min(minimumX, sample.x());
            maximumX = Math.max(maximumX, sample.x());
            minimumZ = Math.min(minimumZ, sample.z());
            maximumZ = Math.max(maximumZ, sample.z());
            minimumDistance = Math.min(minimumDistance, distance);
            maximumDistance = Math.max(maximumDistance, distance);

            int bucket = bucketIndex(distance, bucketSize);
            sparseBuckets.merge(bucket, 1L, Long::sum);
            Quadrant quadrant = quadrant(sample.x(), sample.z());
            quadrantCounts.merge(quadrant, 1L, Long::sum);
            categoryCounts.merge(sample.category(), 1L, Long::sum);
            regionCounts.merge(sample.requestedRegion(), 1L, Long::sum);
            dimensionCounts.merge(sample.dimension(), 1L, Long::sum);
        }

        int maximumBucket = sparseBuckets.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<RadialBucket> radialBuckets = new ArrayList<>(maximumBucket + 1);
        for (int index = 0; index <= maximumBucket; index++) {
            double minimum = index * bucketSize;
            radialBuckets.add(new RadialBucket(
                    minimum,
                    minimum + bucketSize,
                    sparseBuckets.getOrDefault(index, 0L)
            ));
        }

        long count = samples.size();
        return new RtpStatistics(
                scope,
                sourceRevision,
                count,
                xSum.value() / count,
                zSum.value() / count,
                minimumX,
                maximumX,
                minimumZ,
                maximumZ,
                distanceSum.value() / count,
                minimumDistance,
                maximumDistance,
                radialBuckets,
                percentages(quadrantCounts, count),
                categoryCounts,
                regionCounts,
                dimensionCounts
        );
    }

    /** Minecraft convention: north is negative Z and east is positive X. */
    private static Quadrant quadrant(double x, double z) {
        if (z < 0.0) {
            return x >= 0.0 ? Quadrant.NORTH_EAST : Quadrant.NORTH_WEST;
        }
        return x >= 0.0 ? Quadrant.SOUTH_EAST : Quadrant.SOUTH_WEST;
    }

    private static int bucketIndex(double distance, double bucketSize) {
        double index = Math.floor(distance / bucketSize);
        if (index > Integer.MAX_VALUE - 1.0) {
            throw new IllegalArgumentException("Coordinate range is too large for radial statistics");
        }
        return (int) index;
    }

    private static Map<Quadrant, QuadrantStatistics> percentages(
            EnumMap<Quadrant, Long> counts,
            long total
    ) {
        EnumMap<Quadrant, QuadrantStatistics> result = new EnumMap<>(Quadrant.class);
        for (Quadrant quadrant : Quadrant.values()) {
            long count = counts.getOrDefault(quadrant, 0L);
            double percentage = total == 0 ? 0.0 : count * 100.0 / total;
            result.put(quadrant, new QuadrantStatistics(count, percentage));
        }
        return result;
    }

    private record CacheEntry(List<RtpSample> samples, long sourceRevision, RtpStatistics statistics) {
    }

    private static final class KahanSum {
        private double sum;
        private double correction;

        void add(double value) {
            double adjusted = value - correction;
            double next = sum + adjusted;
            correction = (next - sum) - adjusted;
            sum = next;
        }

        double value() {
            return sum;
        }
    }
}
