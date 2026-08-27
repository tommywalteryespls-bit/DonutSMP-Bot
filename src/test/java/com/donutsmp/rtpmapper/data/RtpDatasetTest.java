package com.donutsmp.rtpmapper.data;

import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtpDatasetTest {
    @Test
    void samplesAreImmutablePreciseAndCategorized() {
        double preciseX = -153284.42123456789;
        RtpSample sample = new RtpSample(
                125, preciseX, 68.125, 72482.13000000001,
                "minecraft:overworld", 1787281234000L,
                RtpRegion.EU_CENTRAL, SampleCategory.REGION_1);

        assertEquals(preciseX, sample.x());
        assertEquals(RtpRegion.EU_CENTRAL, sample.requestedRegion());
        assertEquals(SampleCategory.REGION_1, sample.category());
        assertTrue(sample.hasY());
        assertThrows(IllegalArgumentException.class, () ->
                new RtpSample(0, 1, 2, 3, "minecraft:overworld", 1));
        assertThrows(IllegalArgumentException.class, () ->
                new RtpSample(1, Double.NaN, 2, 3, "minecraft:overworld", 1));
        assertEquals(RtpRegion.UNKNOWN,
                new RtpSample(1, 1, 2, 3, "minecraft:overworld", 1).requestedRegion());
    }

    @Test
    void loadedDataIsAllTimeOnlyAndSnapshotsAreRevisionCached() {
        RtpSample old = new RtpSample(7, 1, 2, 3, "minecraft:overworld", 10);
        RtpDataset dataset = new RtpDataset(List.of(old));

        RtpDatasetSnapshot first = dataset.snapshot();
        assertEquals(1, first.totalCount());
        assertEquals(0, first.sessionCount());
        assertSame(first, dataset.snapshot());

        RtpSample added = dataset.addSample(
                4, 5, 6, "minecraft:overworld", 11, RtpRegion.OCEANIA);
        assertEquals(8, added.sampleNumber());
        assertEquals(RtpRegion.OCEANIA, added.requestedRegion());
        RtpDatasetSnapshot second = dataset.snapshot();
        assertNotSame(first, second);
        assertEquals(2, second.totalCount());
        assertEquals(1, second.sessionCount());
        assertThrows(UnsupportedOperationException.class, () -> second.allTimeSamples().clear());
    }

    @Test
    void newSessionPreservesAllTimeAndClearResetsNumbering() {
        RtpDataset dataset = new RtpDataset();
        dataset.addSample(1, 2, 3, "minecraft:overworld", 1);
        dataset.startNewSession();

        assertEquals(1, dataset.size());
        assertEquals(0, dataset.sessionSize());

        dataset.clearAll();
        assertEquals(1, dataset.nextSampleNumber());
        assertEquals(0, dataset.size());
    }

    @Test
    void duplicateSampleNumbersAreRejected() {
        RtpDataset dataset = new RtpDataset();
        RtpSample sample = new RtpSample(1, 1, 2, 3, "minecraft:overworld", 1);
        dataset.add(sample);
        assertThrows(IllegalArgumentException.class, () -> dataset.add(sample));
    }
}
