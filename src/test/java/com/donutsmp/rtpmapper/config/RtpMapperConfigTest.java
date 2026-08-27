package com.donutsmp.rtpmapper.config;

import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtpMapperConfigTest {
    @Test
    void defaultsMatchTheSpecification() {
        RtpMapperConfig config = RtpMapperConfig.defaults();

        assertEquals(5.0, config.rtpIntervalSeconds());
        assertEquals(20.0, config.teleportTimeoutSeconds());
        assertEquals(0.75, config.stabilizationSeconds());
        assertTrue(config.showHud());
        assertFalse(config.autoResume());
        assertTrue(config.storeYCoordinate());
        assertTrue(config.showGrid());
        assertTrue(config.showDistanceRings());
        assertFalse(config.stopNearCenter());
        assertEquals(50_000.0, config.centerStopRadiusBlocks());
        assertFalse(config.stopNearWorldBorder());
        assertEquals(10_000.0, config.worldBorderMarginBlocks());
        assertEquals(RtpRegion.selectableValues(), config.selectedRegions());
        assertEquals(List.of("donutsmp.net", "*.donutsmp.net"), config.allowedServers());
        assertTrue(config.allowSingleplayerMining());
        assertEquals(List.of(), config.miningAllowedServers());
        assertEquals(List.of(
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore"
        ), config.miningBlockIds());
        assertEquals(64, config.miningQuantity());
        assertEquals(10.0, config.miningTimeoutMinutes());
    }

    @Test
    void constructorRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().rtpIntervalSeconds(0.999).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().rtpIntervalSeconds(60.001).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().pointSize(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().centerStopRadiusBlocks(-1).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder()
                        .centerStopRadiusBlocks(RtpMapperConfig.MAX_CENTER_STOP_RADIUS_BLOCKS + 1)
                        .build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().worldBorderMarginBlocks(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder()
                        .worldBorderMarginBlocks(RtpMapperConfig.WORLD_BORDER_LIMIT_BLOCKS + 1)
                        .build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().allowedServers(List.of("foo.*.example.com")).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().selectedRegions(List.of()).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().selectedRegions(List.of(RtpRegion.UNKNOWN)).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().miningQuantity(0).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().miningTimeoutMinutes(121).build());
        assertThrows(IllegalArgumentException.class, () ->
                RtpMapperConfig.builder().miningBlockIds(List.of("bad block id")).build());
    }

    @Test
    void builderRoundTripPreservesAndUpdatesValues() {
        RtpMapperConfig updated = RtpMapperConfig.defaults().toBuilder()
                .rtpIntervalSeconds(12.5)
                .showHud(false)
                .stopNearCenter(true)
                .centerStopRadiusBlocks(60_000)
                .stopNearWorldBorder(true)
                .worldBorderMarginBlocks(15_000)
                .selectedRegions(List.of(RtpRegion.OCEANIA, RtpRegion.NA_EAST))
                .allowedServers(List.of("PLAY.Example.COM."))
                .allowSingleplayerMining(false)
                .miningAllowedServers(List.of("PRIVATE.Example.COM:25565"))
                .miningBlockIds(List.of("ancient_debris"))
                .miningQuantity(18)
                .miningTimeoutMinutes(22.5)
                .build();

        assertEquals(12.5, updated.rtpIntervalSeconds());
        assertFalse(updated.showHud());
        assertTrue(updated.stopNearCenter());
        assertEquals(60_000.0, updated.centerStopRadiusBlocks());
        assertTrue(updated.stopNearWorldBorder());
        assertEquals(15_000.0, updated.worldBorderMarginBlocks());
        assertEquals(List.of(RtpRegion.NA_EAST, RtpRegion.OCEANIA), updated.selectedRegions());
        assertEquals(List.of("play.example.com"), updated.allowedServers());
        assertFalse(updated.allowSingleplayerMining());
        assertEquals(List.of("private.example.com"), updated.miningAllowedServers());
        assertEquals(List.of("minecraft:ancient_debris"), updated.miningBlockIds());
        assertEquals(18, updated.miningQuantity());
        assertEquals(22.5, updated.miningTimeoutMinutes());
    }
}
