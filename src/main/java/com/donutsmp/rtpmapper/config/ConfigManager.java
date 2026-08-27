package com.donutsmp.rtpmapper.config;

import com.donutsmp.rtpmapper.data.AtomicFileIO;
import com.donutsmp.rtpmapper.region.RtpRegion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Loads, validates, migrates, and atomically saves {@code config.json}. */
public final class ConfigManager {
    public static final String DIRECTORY_NAME = "rtpmapper";
    public static final String FILE_NAME = "config.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;
    private final Path configFile;
    private final Clock clock;

    public ConfigManager() {
        this(FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY_NAME));
    }

    /** The supplied path is the {@code rtpmapper} data directory itself. */
    public ConfigManager(Path directory) {
        this(directory, Clock.systemUTC());
    }

    public ConfigManager(Path directory, Clock clock) {
        this.directory = directory.toAbsolutePath();
        this.configFile = this.directory.resolve(FILE_NAME);
        this.clock = clock;
    }

    public synchronized RtpMapperConfig load() throws IOException {
        Files.createDirectories(directory);
        AtomicFileIO.recoverMissingTarget(configFile);
        if (!Files.exists(configFile)) {
            RtpMapperConfig defaults = RtpMapperConfig.defaults();
            save(defaults);
            return defaults;
        }

        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(configFile, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Config root must be a JSON object");
            }
            RtpMapperConfig config = decode(parsed.getAsJsonObject());
            // A readable, valid config remains usable even when best-effort
            // cleanup/normalization cannot be persisted (for example, on a
            // temporarily read-only filesystem).
            try {
                AtomicFileIO.discardJournal(configFile);
                save(config);
            } catch (IOException ignored) {
                // A future settings save or launch will retry normalization.
            }
            return config;
        } catch (RuntimeException exception) {
            Path journal = AtomicFileIO.journalPath(configFile);
            if (Files.exists(journal)) {
                try {
                    JsonElement parsedJournal = JsonParser.parseString(
                            Files.readString(journal, StandardCharsets.UTF_8));
                    RtpMapperConfig recovered = decode(parsedJournal.getAsJsonObject());
                    AtomicFileIO.restoreJournal(configFile);
                    return recovered;
                } catch (RuntimeException recoveryFailure) {
                    exception.addSuppressed(recoveryFailure);
                }
            }

            backupCorruptConfig();
            RtpMapperConfig defaults = RtpMapperConfig.defaults();
            save(defaults);
            return defaults;
        }
    }

    public synchronized void save(RtpMapperConfig config) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("rtpIntervalSeconds", config.rtpIntervalSeconds());
        root.addProperty("teleportDetectionThresholdBlocks", config.teleportDetectionThresholdBlocks());
        root.addProperty("teleportTimeoutSeconds", config.teleportTimeoutSeconds());
        root.addProperty("stabilizationSeconds", config.stabilizationSeconds());
        root.addProperty("showHud", config.showHud());
        root.addProperty("autoResume", config.autoResume());
        root.addProperty("storeYCoordinate", config.storeYCoordinate());
        root.addProperty("showGrid", config.showGrid());
        root.addProperty("showDistanceRings", config.showDistanceRings());
        root.addProperty("pointSize", config.pointSize());
        root.addProperty("stopNearCenter", config.stopNearCenter());
        root.addProperty("centerStopRadiusBlocks", config.centerStopRadiusBlocks());
        root.addProperty("stopNearWorldBorder", config.stopNearWorldBorder());
        root.addProperty("worldBorderMarginBlocks", config.worldBorderMarginBlocks());
        root.addProperty("allowSingleplayerMining", config.allowSingleplayerMining());
        root.addProperty("miningQuantity", config.miningQuantity());
        root.addProperty("miningTimeoutMinutes", config.miningTimeoutMinutes());
        JsonArray regions = new JsonArray();
        config.selectedRegions().forEach(region -> regions.add(region.id()));
        root.add("selectedRegions", regions);
        JsonArray servers = new JsonArray();
        config.allowedServers().forEach(servers::add);
        root.add("allowedServers", servers);
        JsonArray miningServers = new JsonArray();
        config.miningAllowedServers().forEach(miningServers::add);
        root.add("miningAllowedServers", miningServers);
        JsonArray miningBlocks = new JsonArray();
        config.miningBlockIds().forEach(miningBlocks::add);
        root.add("miningBlockIds", miningBlocks);
        AtomicFileIO.writeUtf8(configFile, GSON.toJson(root) + System.lineSeparator());
    }

    public Path directory() {
        return directory;
    }

    public Path configFile() {
        return configFile;
    }

    private RtpMapperConfig decode(JsonObject root) {
        RtpMapperConfig defaults = RtpMapperConfig.defaults();
        return RtpMapperConfig.builder()
                .rtpIntervalSeconds(readClampedDouble(root, "rtpIntervalSeconds",
                        defaults.rtpIntervalSeconds(), RtpMapperConfig.MIN_RTP_INTERVAL_SECONDS,
                        RtpMapperConfig.MAX_RTP_INTERVAL_SECONDS))
                .teleportDetectionThresholdBlocks(readClampedDouble(root,
                        "teleportDetectionThresholdBlocks", defaults.teleportDetectionThresholdBlocks(),
                        RtpMapperConfig.MIN_TELEPORT_THRESHOLD_BLOCKS,
                        RtpMapperConfig.MAX_TELEPORT_THRESHOLD_BLOCKS))
                .teleportTimeoutSeconds(readClampedDouble(root, "teleportTimeoutSeconds",
                        defaults.teleportTimeoutSeconds(), RtpMapperConfig.MIN_TELEPORT_TIMEOUT_SECONDS,
                        RtpMapperConfig.MAX_TELEPORT_TIMEOUT_SECONDS))
                .stabilizationSeconds(readClampedDouble(root, "stabilizationSeconds",
                        defaults.stabilizationSeconds(), RtpMapperConfig.MIN_STABILIZATION_SECONDS,
                        RtpMapperConfig.MAX_STABILIZATION_SECONDS))
                .showHud(readBoolean(root, "showHud", defaults.showHud()))
                .autoResume(readBoolean(root, "autoResume", defaults.autoResume()))
                .storeYCoordinate(readBoolean(root, "storeYCoordinate", defaults.storeYCoordinate()))
                .showGrid(readBoolean(root, "showGrid", defaults.showGrid()))
                .showDistanceRings(readBoolean(root, "showDistanceRings", defaults.showDistanceRings()))
                .pointSize(readClampedDouble(root, "pointSize", defaults.pointSize(),
                        RtpMapperConfig.MIN_POINT_SIZE, RtpMapperConfig.MAX_POINT_SIZE))
                .stopNearCenter(readBoolean(root, "stopNearCenter", defaults.stopNearCenter()))
                .centerStopRadiusBlocks(readClampedDouble(root, "centerStopRadiusBlocks",
                        defaults.centerStopRadiusBlocks(), RtpMapperConfig.MIN_CENTER_STOP_RADIUS_BLOCKS,
                        RtpMapperConfig.MAX_CENTER_STOP_RADIUS_BLOCKS))
                .stopNearWorldBorder(readBoolean(root, "stopNearWorldBorder",
                        defaults.stopNearWorldBorder()))
                .worldBorderMarginBlocks(readClampedDouble(root, "worldBorderMarginBlocks",
                        defaults.worldBorderMarginBlocks(), RtpMapperConfig.MIN_WORLD_BORDER_MARGIN_BLOCKS,
                        RtpMapperConfig.MAX_WORLD_BORDER_MARGIN_BLOCKS))
                .allowSingleplayerMining(readBoolean(root, "allowSingleplayerMining",
                        defaults.allowSingleplayerMining()))
                .miningAllowedServers(readOptionalServers(root, "miningAllowedServers",
                        defaults.miningAllowedServers()))
                .miningBlockIds(readMiningBlocks(root, defaults.miningBlockIds()))
                .miningQuantity(readClampedInt(root, "miningQuantity", defaults.miningQuantity(),
                        RtpMapperConfig.MIN_MINING_QUANTITY, RtpMapperConfig.MAX_MINING_QUANTITY))
                .miningTimeoutMinutes(readClampedDouble(root, "miningTimeoutMinutes",
                        defaults.miningTimeoutMinutes(), RtpMapperConfig.MIN_MINING_TIMEOUT_MINUTES,
                        RtpMapperConfig.MAX_MINING_TIMEOUT_MINUTES))
                .selectedRegions(readRegions(root, defaults.selectedRegions()))
                .allowedServers(readServers(root, defaults.allowedServers()))
                .build();
    }

    private static double readClampedDouble(
            JsonObject root,
            String key,
            double fallback,
            double minimum,
            double maximum
    ) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            double value = element.getAsDouble();
            if (!Double.isFinite(value)) {
                return fallback;
            }
            return Math.clamp(value, minimum, maximum);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static int readClampedInt(
            JsonObject root,
            String key,
            int fallback,
            int minimum,
            int maximum
    ) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            int value = element.getAsInt();
            return Math.clamp(value, minimum, maximum);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static List<String> readOptionalServers(
            JsonObject root,
            String key,
            List<String> fallback
    ) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        List<String> patterns = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                continue;
            }
            try {
                patterns.add(ServerMatcher.normalizePattern(entry.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // Invalid mining patterns fail closed; an empty result is valid.
            }
        }
        return ServerMatcher.normalizeOptionalPatterns(patterns);
    }

    private static List<String> readMiningBlocks(JsonObject root, List<String> fallback) {
        JsonElement element = root.get("miningBlockIds");
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        List<String> blocks = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                blocks.add(entry.getAsString());
            }
        }
        try {
            return com.donutsmp.rtpmapper.mining.MiningSettings.normalizeBlockIds(blocks);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static List<String> readServers(JsonObject root, List<String> fallback) {
        JsonElement element = root.get("allowedServers");
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        List<String> patterns = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                continue;
            }
            try {
                patterns.add(ServerMatcher.normalizePattern(entry.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // Invalid entries fail closed; valid entries remain usable.
            }
        }
        return patterns.isEmpty() ? fallback : patterns;
    }

    private static List<RtpRegion> readRegions(JsonObject root, List<RtpRegion> fallback) {
        JsonElement element = root.get("selectedRegions");
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        List<RtpRegion> regions = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                continue;
            }
            RtpRegion.fromId(entry.getAsString())
                    .filter(RtpRegion::selectable)
                    .ifPresent(regions::add);
        }
        return regions.isEmpty() ? fallback : RtpRegion.normalizeSelection(regions);
    }

    private void backupCorruptConfig() throws IOException {
        if (!Files.exists(configFile)) {
            return;
        }
        String suffix = ".corrupt-" + clock.millis();
        Path backup = configFile.resolveSibling(configFile.getFileName() + suffix);
        Files.move(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
    }
}
