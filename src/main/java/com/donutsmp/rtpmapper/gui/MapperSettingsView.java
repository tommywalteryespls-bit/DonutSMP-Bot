package com.donutsmp.rtpmapper.gui;

import com.donutsmp.rtpmapper.config.RtpMapperConfig;
import com.donutsmp.rtpmapper.region.RtpRegion;
import java.util.List;
import java.util.Objects;

public record MapperSettingsView(
    double intervalSeconds,
    double teleportThresholdBlocks,
    double teleportTimeoutSeconds,
    double stabilizationSeconds,
    boolean showHud,
    boolean autoResume,
    boolean storeY,
    boolean showGrid,
    boolean showDistanceRings,
    double pointSize,
    List<String> allowedServers,
    List<RtpRegion> selectedRegions,
    boolean stopNearCenter,
    double centerStopRadiusBlocks,
    boolean stopNearWorldBorder,
    double worldBorderMarginBlocks
) {
    public MapperSettingsView {
        requireRange("RTP interval", intervalSeconds, 1.0, 60.0);
        requireRange("Teleport threshold", teleportThresholdBlocks, 32.0, 60_000_000.0);
        requireRange("Teleport timeout", teleportTimeoutSeconds, 5.0, 300.0);
        requireRange("Stabilization time", stabilizationSeconds, 0.25, 5.0);
        if (!Double.isFinite(pointSize) || pointSize < 1 || pointSize > 8) {
            throw new IllegalArgumentException("Point size must be from 1 to 8.");
        }
        requireRange("Center stop radius", centerStopRadiusBlocks,
            RtpMapperConfig.MIN_CENTER_STOP_RADIUS_BLOCKS,
            RtpMapperConfig.MAX_CENTER_STOP_RADIUS_BLOCKS);
        requireRange("World border margin", worldBorderMarginBlocks,
            RtpMapperConfig.MIN_WORLD_BORDER_MARGIN_BLOCKS,
            RtpMapperConfig.MAX_WORLD_BORDER_MARGIN_BLOCKS);
        Objects.requireNonNull(allowedServers, "allowedServers");
        allowedServers = allowedServers.stream()
            .map(value -> Objects.requireNonNull(value, "allowed server").trim())
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
        if (allowedServers.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed server is required.");
        }
        selectedRegions = RtpRegion.normalizeSelection(selectedRegions);
    }

    /** Compatibility constructor for callers that predate coordinate stop guards. */
    public MapperSettingsView(
        double intervalSeconds,
        double teleportThresholdBlocks,
        double teleportTimeoutSeconds,
        double stabilizationSeconds,
        boolean showHud,
        boolean autoResume,
        boolean storeY,
        boolean showGrid,
        boolean showDistanceRings,
        double pointSize,
        List<String> allowedServers,
        List<RtpRegion> selectedRegions
    ) {
        this(
            intervalSeconds,
            teleportThresholdBlocks,
            teleportTimeoutSeconds,
            stabilizationSeconds,
            showHud,
            autoResume,
            storeY,
            showGrid,
            showDistanceRings,
            pointSize,
            allowedServers,
            selectedRegions,
            false,
            RtpMapperConfig.DEFAULT_CENTER_STOP_RADIUS_BLOCKS,
            false,
            RtpMapperConfig.DEFAULT_WORLD_BORDER_MARGIN_BLOCKS
        );
    }

    /** Compatibility constructor for callers that predate region selection. */
    public MapperSettingsView(
        double intervalSeconds,
        double teleportThresholdBlocks,
        double teleportTimeoutSeconds,
        double stabilizationSeconds,
        boolean showHud,
        boolean autoResume,
        boolean storeY,
        boolean showGrid,
        boolean showDistanceRings,
        double pointSize,
        List<String> allowedServers
    ) {
        this(
            intervalSeconds,
            teleportThresholdBlocks,
            teleportTimeoutSeconds,
            stabilizationSeconds,
            showHud,
            autoResume,
            storeY,
            showGrid,
            showDistanceRings,
            pointSize,
            allowedServers,
            RtpRegion.selectableValues(),
            false,
            RtpMapperConfig.DEFAULT_CENTER_STOP_RADIUS_BLOCKS,
            false,
            RtpMapperConfig.DEFAULT_WORLD_BORDER_MARGIN_BLOCKS
        );
    }

    public static MapperSettingsView defaults() {
        return new MapperSettingsView(
            5.0,
            512.0,
            20.0,
            0.75,
            true,
            false,
            true,
            true,
            true,
            2.5,
            List.of("donutsmp.net", "*.donutsmp.net"),
            RtpRegion.selectableValues(),
            false,
            RtpMapperConfig.DEFAULT_CENTER_STOP_RADIUS_BLOCKS,
            false,
            RtpMapperConfig.DEFAULT_WORLD_BORDER_MARGIN_BLOCKS
        );
    }

    private static void requireRange(String label, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be from " + minimum + " to " + maximum + ".");
        }
    }
}
