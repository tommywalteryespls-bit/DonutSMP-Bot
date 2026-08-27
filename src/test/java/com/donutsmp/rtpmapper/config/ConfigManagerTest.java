package com.donutsmp.rtpmapper.config;

import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingConfigCreatesValidatedDefaults() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        RtpMapperConfig config = manager.load();

        assertEquals(RtpMapperConfig.defaults(), config);
        assertTrue(Files.exists(manager.configFile()));
        assertEquals(config, manager.load());
    }

    @Test
    void loadMergesDefaultsClampsRangesAndDropsBadPatterns() throws Exception {
        Files.writeString(temporaryDirectory.resolve("config.json"), """
                {
                  "rtpIntervalSeconds": 0,
                  "pointSize": 99,
                  "showHud": false,
                  "selectedRegions": ["oceania", "invalid", "NA-EAST", "oceania", "unknown"],
                  "allowedServers": ["foo.*.invalid", "play.example.com:25565"]
                }
                """);

        RtpMapperConfig config = new ConfigManager(temporaryDirectory).load();

        assertEquals(RtpMapperConfig.MIN_RTP_INTERVAL_SECONDS, config.rtpIntervalSeconds());
        assertEquals(RtpMapperConfig.MAX_POINT_SIZE, config.pointSize());
        assertEquals(RtpMapperConfig.DEFAULT_TELEPORT_TIMEOUT_SECONDS,
                config.teleportTimeoutSeconds());
        assertEquals(false, config.stopNearCenter());
        assertEquals(RtpMapperConfig.DEFAULT_CENTER_STOP_RADIUS_BLOCKS,
                config.centerStopRadiusBlocks());
        assertEquals(false, config.stopNearWorldBorder());
        assertEquals(RtpMapperConfig.DEFAULT_WORLD_BORDER_MARGIN_BLOCKS,
                config.worldBorderMarginBlocks());
        assertEquals(List.of(RtpRegion.NA_EAST, RtpRegion.OCEANIA), config.selectedRegions());
        assertEquals(java.util.List.of("play.example.com"), config.allowedServers());
    }

    @Test
    void absentOrEntirelyInvalidRegionSelectionFallsBackToAllPublicRegions() throws Exception {
        Files.writeString(temporaryDirectory.resolve("config.json"), """
                {"selectedRegions": ["unknown", "future-region", 42]}
                """);

        ConfigManager manager = new ConfigManager(temporaryDirectory);
        RtpMapperConfig config = manager.load();

        assertEquals(RtpRegion.selectableValues(), config.selectedRegions());
        String normalized = Files.readString(manager.configFile());
        assertTrue(normalized.contains("\"na_east\""));
        assertTrue(normalized.contains("\"oceania\""));
    }

    @Test
    void selectedRegionsRoundTripThroughCanonicalIds() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        RtpMapperConfig expected = RtpMapperConfig.defaults().toBuilder()
                .selectedRegions(List.of(RtpRegion.EU_WEST, RtpRegion.ASIA))
                .stopNearCenter(true)
                .centerStopRadiusBlocks(75_000)
                .stopNearWorldBorder(true)
                .worldBorderMarginBlocks(12_500)
                .allowSingleplayerMining(false)
                .miningAllowedServers(List.of("private.example.net", "*.lan.example.net"))
                .miningBlockIds(List.of("minecraft:ancient_debris"))
                .miningQuantity(12)
                .miningTimeoutMinutes(15)
                .build();

        manager.save(expected);

        assertEquals(expected, manager.load());
    }

    @Test
    void legacyConfigMigratesToFailClosedMultiplayerMiningDefaults() throws Exception {
        Files.writeString(temporaryDirectory.resolve("config.json"), """
                {
                  "showHud": false,
                  "miningAllowedServers": [],
                  "miningBlockIds": ["diamond_ore", "bad block", "deepslate_diamond_ore"],
                  "miningQuantity": 999999,
                  "miningTimeoutMinutes": 0
                }
                """);

        RtpMapperConfig config = new ConfigManager(temporaryDirectory).load();

        assertFalse(config.showHud());
        assertTrue(config.allowSingleplayerMining());
        assertEquals(List.of(), config.miningAllowedServers());
        assertEquals(RtpMapperConfig.DEFAULT_MINING_BLOCK_IDS, config.miningBlockIds());
        assertEquals(RtpMapperConfig.MAX_MINING_QUANTITY, config.miningQuantity());
        assertEquals(RtpMapperConfig.MIN_MINING_TIMEOUT_MINUTES, config.miningTimeoutMinutes());
    }

    @Test
    void guardRangesAreClampedAndMissingBooleansRemainDisabled() throws Exception {
        Files.writeString(temporaryDirectory.resolve("config.json"), """
                {
                  "centerStopRadiusBlocks": 999999999,
                  "worldBorderMarginBlocks": -50
                }
                """);

        RtpMapperConfig config = new ConfigManager(temporaryDirectory).load();

        assertEquals(RtpMapperConfig.MAX_CENTER_STOP_RADIUS_BLOCKS,
                config.centerStopRadiusBlocks());
        assertEquals(RtpMapperConfig.MIN_WORLD_BORDER_MARGIN_BLOCKS,
                config.worldBorderMarginBlocks());
        assertEquals(false, config.stopNearCenter());
        assertEquals(false, config.stopNearWorldBorder());
        String normalized = Files.readString(temporaryDirectory.resolve("config.json"));
        assertTrue(normalized.contains("\"stopNearCenter\": false"));
        assertTrue(normalized.contains("\"stopNearWorldBorder\": false"));
    }

    @Test
    void corruptConfigIsPreservedBeforeDefaultsAreWritten() throws Exception {
        Files.writeString(temporaryDirectory.resolve("config.json"), "not json");
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC);
        ConfigManager manager = new ConfigManager(temporaryDirectory, clock);

        assertEquals(RtpMapperConfig.defaults(), manager.load());
        assertTrue(Files.exists(temporaryDirectory.resolve("config.json.corrupt-1234")));
        assertTrue(Files.exists(manager.configFile()));
    }
}
