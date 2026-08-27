package com.donutsmp.rtpmapper.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RtpRegionTest {
    @Test
    void exposesStablePublicRegionMetadata() {
        assertEquals(List.of(
                RtpRegion.NA_EAST,
                RtpRegion.NA_WEST,
                RtpRegion.EU_CENTRAL,
                RtpRegion.EU_WEST,
                RtpRegion.ASIA,
                RtpRegion.OCEANIA
        ), RtpRegion.selectableValues());
        assertEquals("na_east", RtpRegion.NA_EAST.id());
        assertEquals("North America East", RtpRegion.NA_EAST.displayName());
        assertEquals("NA East", RtpRegion.NA_EAST.shortName());
        assertEquals("east", RtpRegion.NA_EAST.commandArgument());
        assertEquals(
                List.of("east", "west", "eu central", "eu west", "asia", "oceania"),
                RtpRegion.selectableValues().stream().map(RtpRegion::commandArgument).toList()
        );
        assertEquals(0xFF4FA9DD, RtpRegion.NA_EAST.colorArgb());
        assertEquals(RtpRegion.UNKNOWN, RtpRegion.displayValues().getLast());
        assertFalse(RtpRegion.UNKNOWN.selectable());
        assertThrows(IllegalStateException.class, RtpRegion.UNKNOWN::commandArgument);
    }

    @Test
    void parsesCanonicalIdsAndTolerantAliasesWithoutInventingValues() {
        assertEquals(RtpRegion.NA_EAST, RtpRegion.fromId("NA-EAST").orElseThrow());
        assertEquals(RtpRegion.NA_EAST, RtpRegion.fromId("east").orElseThrow());
        assertEquals(RtpRegion.EU_CENTRAL, RtpRegion.fromId("EU Central").orElseThrow());
        assertEquals(RtpRegion.UNKNOWN, RtpRegion.parseOrUnknown("future_region"));
        assertTrue(RtpRegion.fromId(" ").isEmpty());
    }

    @Test
    void selectionIsImmutableDeduplicatedCanonicalAndNonempty() {
        List<RtpRegion> normalized = RtpRegion.normalizeSelection(List.of(
                RtpRegion.OCEANIA,
                RtpRegion.NA_EAST,
                RtpRegion.OCEANIA,
                RtpRegion.EU_WEST
        ));
        assertEquals(List.of(RtpRegion.NA_EAST, RtpRegion.EU_WEST, RtpRegion.OCEANIA), normalized);
        assertThrows(UnsupportedOperationException.class, () -> normalized.add(RtpRegion.ASIA));
        assertThrows(IllegalArgumentException.class, () -> RtpRegion.normalizeSelection(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> RtpRegion.normalizeSelection(List.of(RtpRegion.UNKNOWN)));
    }
}
