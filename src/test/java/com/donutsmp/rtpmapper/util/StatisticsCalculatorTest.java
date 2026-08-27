package com.donutsmp.rtpmapper.util;

import com.donutsmp.rtpmapper.data.RtpDataset;
import com.donutsmp.rtpmapper.data.RtpSample;
import com.donutsmp.rtpmapper.data.SampleCategory;
import com.donutsmp.rtpmapper.data.SampleScope;
import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsCalculatorTest {
    @Test
    void computesCoordinateRadialQuadrantCategoryAndDimensionStatistics() {
        List<RtpSample> samples = List.of(
                sample(1, 3_000, -4_000, SampleCategory.DEFAULT, RtpRegion.NA_EAST),
                sample(2, -30_000, -40_000, SampleCategory.REGION_1, RtpRegion.EU_WEST),
                sample(3, 0, 0, SampleCategory.DEFAULT, RtpRegion.UNKNOWN),
                sample(4, -60_000, 80_000, SampleCategory.REGION_1, RtpRegion.EU_WEST)
        );

        RtpStatistics stats = StatisticsCalculator.compute(samples, SampleScope.ALL_TIME);

        assertEquals(4, stats.totalSamples());
        assertEquals(-21_750.0, stats.averageX());
        assertEquals(9_000.0, stats.averageZ());
        assertEquals(-60_000.0, stats.minimumX());
        assertEquals(3_000.0, stats.maximumX());
        assertEquals(-40_000.0, stats.minimumZ());
        assertEquals(80_000.0, stats.maximumZ());
        assertEquals(38_750.0, stats.averageDistance());
        assertEquals(0.0, stats.minimumDistance());
        assertEquals(100_000.0, stats.maximumDistance());

        assertEquals(5, stats.radialBuckets().size());
        assertEquals(List.of(2L, 0L, 1L, 0L, 1L),
                stats.radialBuckets().stream().map(RadialBucket::count).toList());
        assertEquals("0-25k", stats.radialBuckets().getFirst().label());
        for (Quadrant quadrant : Quadrant.values()) {
            assertEquals(1, stats.quadrant(quadrant).count());
            assertEquals(25.0, stats.quadrant(quadrant).percentage());
        }
        assertEquals(2L, stats.categoryCounts().get(SampleCategory.DEFAULT));
        assertEquals(2L, stats.categoryCounts().get(SampleCategory.REGION_1));
        assertEquals(1L, stats.regionCounts().get(RtpRegion.NA_EAST));
        assertEquals(2L, stats.regionCounts().get(RtpRegion.EU_WEST));
        assertEquals(1L, stats.regionCounts().get(RtpRegion.UNKNOWN));
        assertEquals(0L, stats.regionCounts().get(RtpRegion.OCEANIA));
        assertEquals(4L, stats.dimensionCounts().get("minecraft:overworld"));
    }

    @Test
    void cachesByScopeListIdentityAndRevision() {
        RtpDataset dataset = new RtpDataset();
        dataset.addSample(1, 2, 3, "minecraft:overworld", 1);
        StatisticsCalculator calculator = new StatisticsCalculator();

        RtpStatistics first = calculator.calculate(dataset.snapshot(), SampleScope.ALL_TIME);
        assertSame(first, calculator.calculate(dataset.snapshot(), SampleScope.ALL_TIME));

        dataset.addSample(4, 5, 6, "minecraft:overworld", 2);
        RtpStatistics second = calculator.calculate(dataset.snapshot(), SampleScope.ALL_TIME);
        assertNotSame(first, second);
        assertEquals(2, second.totalSamples());

        dataset.startNewSession();
        assertSame(second, calculator.calculate(dataset.snapshot(), SampleScope.ALL_TIME),
                "Clearing only the session should retain the all-time cache");
    }

    @Test
    void emptyStatisticsAreExplicit() {
        RtpStatistics empty = StatisticsCalculator.compute(List.of(), SampleScope.SESSION);

        assertFalse(empty.hasSamples());
        assertTrue(Double.isNaN(empty.averageX()));
        assertTrue(empty.radialBuckets().isEmpty());
        assertEquals(0.0, empty.quadrant(Quadrant.NORTH_EAST).percentage());
        for (RtpRegion region : RtpRegion.displayValues()) {
            assertEquals(0L, empty.regionCounts().get(region));
        }
    }

    private static RtpSample sample(
            long number,
            double x,
            double z,
            SampleCategory category,
            RtpRegion requestedRegion
    ) {
        return new RtpSample(
                number,
                x,
                64,
                z,
                "minecraft:overworld",
                number,
                requestedRegion,
                category
        );
    }
}
