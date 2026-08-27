package com.donutsmp.rtpmapper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.donutsmp.rtpmapper.region.RtpRegion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapperSettingsViewTest {
    @Test
    void invalidValuesAreRejectedInsteadOfSilentlyClamped() {
        assertThrows(IllegalArgumentException.class, () -> view(999.0, 3));
        assertThrows(IllegalArgumentException.class, () -> view(5.0, 99));
        assertThrows(IllegalArgumentException.class, () -> new MapperSettingsView(
            5.0, 512.0, 20.0, 0.75,
            true, false, true, true, true, 3, List.of()
        ));
    }

    @Test
    void validValuesAndNormalizedHostsArePreserved() {
        MapperSettingsView settings = view(12.5, 4);

        assertEquals(12.5, settings.intervalSeconds());
        assertEquals(4.0, settings.pointSize());
        assertEquals(List.of("DonutSMP.net", "*.donutsmp.net"), settings.allowedServers());
    }

    @Test
    void defaultsSelectAllSixPublicRegions() {
        assertIterableEquals(
            RtpRegion.selectableValues(),
            MapperSettingsView.defaults().selectedRegions()
        );
        assertEquals(false, MapperSettingsView.defaults().stopNearCenter());
        assertEquals(50_000.0, MapperSettingsView.defaults().centerStopRadiusBlocks());
        assertEquals(false, MapperSettingsView.defaults().stopNearWorldBorder());
        assertEquals(10_000.0, MapperSettingsView.defaults().worldBorderMarginBlocks());
    }

    @Test
    void coordinateGuardValuesAreValidatedAndPreserved() {
        MapperSettingsView settings = guardView(true, 65_000, true, 12_000);

        assertEquals(true, settings.stopNearCenter());
        assertEquals(65_000.0, settings.centerStopRadiusBlocks());
        assertEquals(true, settings.stopNearWorldBorder());
        assertEquals(12_000.0, settings.worldBorderMarginBlocks());
        assertThrows(IllegalArgumentException.class,
            () -> guardView(true, -1, false, 10_000));
        assertThrows(IllegalArgumentException.class,
            () -> guardView(false, 50_000, true, 225_001));
    }

    @Test
    void selectedRegionsAreCanonicalDuplicateFreeAndImmutable() {
        List<RtpRegion> selectable = RtpRegion.selectableValues();
        List<RtpRegion> input = new ArrayList<>(List.of(
            selectable.get(4),
            selectable.get(1),
            selectable.get(4)
        ));
        MapperSettingsView settings = view(5.0, 2.5, input);
        input.clear();

        assertEquals(List.of(selectable.get(1), selectable.get(4)), settings.selectedRegions());
        assertThrows(UnsupportedOperationException.class, () -> settings.selectedRegions().clear());
    }

    @Test
    void emptyAndLegacyUnknownRegionSelectionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> view(5.0, 2.5, List.of()));
        assertThrows(IllegalArgumentException.class, () -> view(5.0, 2.5, List.of(RtpRegion.UNKNOWN)));
    }

    private static MapperSettingsView view(double interval, double pointSize) {
        return view(interval, pointSize, RtpRegion.selectableValues());
    }

    private static MapperSettingsView view(double interval, double pointSize, List<RtpRegion> selectedRegions) {
        return new MapperSettingsView(
            interval, 512.0, 20.0, 0.75,
            true, false, true, true, true, pointSize,
            List.of("DonutSMP.net", "*.donutsmp.net"),
            selectedRegions
        );
    }

    private static MapperSettingsView guardView(
        boolean stopNearCenter,
        double centerRadius,
        boolean stopNearWorldBorder,
        double borderMargin
    ) {
        return new MapperSettingsView(
            5.0, 512.0, 20.0, 0.75,
            true, false, true, true, true, 2.5,
            List.of("donutsmp.net", "*.donutsmp.net"),
            RtpRegion.selectableValues(),
            stopNearCenter, centerRadius, stopNearWorldBorder, borderMargin
        );
    }
}
