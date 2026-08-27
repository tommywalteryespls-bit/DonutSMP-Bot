package com.donutsmp.rtpmapper.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RtpRegionCycleTest {
    @Test
    void cyclesEverySelectedRegionWithoutPeekAdvancing() {
        RtpRegionCycle cycle = new RtpRegionCycle();
        List<RtpRegion> regions = RtpRegion.selectableValues();

        assertEquals(RtpRegion.NA_EAST, cycle.peek(regions));
        assertTrue(cycle.lastIssued().isEmpty());
        for (RtpRegion region : regions) {
            assertEquals(region, cycle.next(regions));
        }
        assertEquals(RtpRegion.NA_EAST, cycle.next(regions));
    }

    @Test
    void seedContinuesAcrossRestartAndChangedSelectionsFailOverToFirst() {
        RtpRegionCycle continued = new RtpRegionCycle(RtpRegion.EU_CENTRAL);
        assertEquals(RtpRegion.EU_WEST, continued.peek(RtpRegion.selectableValues()));
        assertEquals(RtpRegion.EU_WEST, continued.next(RtpRegion.selectableValues()));

        List<RtpRegion> changed = List.of(RtpRegion.NA_WEST, RtpRegion.ASIA);
        assertEquals(RtpRegion.NA_WEST, continued.next(changed));
        assertEquals(RtpRegion.ASIA, continued.next(changed));
        assertEquals(RtpRegion.NA_WEST, continued.next(changed));
    }

    @Test
    void unknownSeedAndSingleSelectionAreSafe() {
        RtpRegionCycle cycle = new RtpRegionCycle(RtpRegion.UNKNOWN);
        List<RtpRegion> onlyOceania = List.of(RtpRegion.OCEANIA);
        assertEquals(RtpRegion.OCEANIA, cycle.next(onlyOceania));
        assertEquals(RtpRegion.OCEANIA, cycle.next(onlyOceania));
        assertEquals(RtpRegion.OCEANIA, cycle.lastIssued().orElseThrow());
    }
}
