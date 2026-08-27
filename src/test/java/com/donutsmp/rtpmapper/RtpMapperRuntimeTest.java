package com.donutsmp.rtpmapper;

import com.donutsmp.rtpmapper.data.RtpDataset;
import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtpMapperRuntimeTest {
    @Test
    void restartCycleSeedsFromLatestKnownRegionNotTrailingLegacyRows() {
        RtpDataset dataset = new RtpDataset();
        dataset.addSample(1, 64, 2, "minecraft:overworld", 1, RtpRegion.NA_WEST);
        dataset.addSample(3, 64, 4, "minecraft:overworld", 2, RtpRegion.UNKNOWN);

        assertEquals(RtpRegion.NA_WEST, RtpMapperRuntime.latestKnownRequestedRegion(dataset));
    }

    @Test
    void restartCycleHasNoSeedWhenEveryHistoricalRegionIsUnknown() {
        RtpDataset dataset = new RtpDataset();
        dataset.addSample(1, 64, 2, "minecraft:overworld", 1);

        assertEquals(RtpRegion.UNKNOWN, RtpMapperRuntime.latestKnownRequestedRegion(dataset));
    }
}
