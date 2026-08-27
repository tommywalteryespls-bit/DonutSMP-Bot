package com.donutsmp.rtpmapper.data;

import com.donutsmp.rtpmapper.region.RtpRegion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Canonical JSON persistence plus a synchronized CSV mirror and exports. */
public final class DataStorage {
    public static final int SCHEMA_VERSION = 2;
    public static final String SAMPLES_JSON_FILE = "rtp_samples.json";
    public static final String SAMPLES_CSV_FILE = "rtp_samples.csv";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataDirectory;
    private final Path samplesJsonFile;
    private final Path samplesCsvFile;
    private final CsvExporter csvExporter;
    private final Clock clock;
    private IOException lastMirrorFailure;

    public DataStorage() {
        this(
                FabricLoader.getInstance().getConfigDir().resolve("rtpmapper"),
                FabricLoader.getInstance().getGameDir().resolve("rtpmapper").resolve("exports"),
                Clock.systemDefaultZone()
        );
    }

    /** Uses {@code dataDirectory/exports} for exports, convenient for isolated tests. */
    public DataStorage(Path dataDirectory) {
        this(dataDirectory, dataDirectory.resolve("exports"), Clock.systemDefaultZone());
    }

    public DataStorage(Path dataDirectory, Path exportDirectory, Clock clock) {
        this.dataDirectory = dataDirectory.toAbsolutePath();
        this.samplesJsonFile = this.dataDirectory.resolve(SAMPLES_JSON_FILE);
        this.samplesCsvFile = this.dataDirectory.resolve(SAMPLES_CSV_FILE);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.csvExporter = new CsvExporter(exportDirectory, clock);
    }

    public synchronized List<RtpSample> loadSamples() throws IOException {
        Files.createDirectories(dataDirectory);
        AtomicFileIO.recoverMissingTarget(samplesJsonFile);
        if (!Files.exists(samplesJsonFile)) {
            return List.of();
        }

        try {
            List<RtpSample> samples = parseSamples(Files.readString(samplesJsonFile, StandardCharsets.UTF_8));
            // A valid canonical file always wins. Cleanup of a stale recovery
            // journal or repair of the derived CSV mirror must never roll the
            // canonical generation back.
            try {
                AtomicFileIO.discardJournal(samplesJsonFile);
            } catch (IOException ignored) {
                // Best effort; the valid primary is parsed again next launch.
            }
            reconcileCsvMirror(samples);
            return samples;
        } catch (RuntimeException | IOException primaryFailure) {
            Path journal = AtomicFileIO.journalPath(samplesJsonFile);
            if (Files.exists(journal)) {
                try {
                    List<RtpSample> recovered = parseSamples(
                            Files.readString(journal, StandardCharsets.UTF_8));
                    AtomicFileIO.restoreJournal(samplesJsonFile);
                    reconcileCsvMirror(recovered);
                    return recovered;
                } catch (RuntimeException | IOException recoveryFailure) {
                    primaryFailure.addSuppressed(recoveryFailure);
                }
            }
            throw new IOException("Unable to load RTP samples without risking data loss", primaryFailure);
        }
    }

    public synchronized RtpDataset loadDataset() throws IOException {
        return new RtpDataset(loadSamples());
    }

    public synchronized void loadInto(RtpDataset dataset) throws IOException {
        dataset.replaceAllTime(loadSamples());
    }

    public synchronized void save(RtpDataset dataset) throws IOException {
        save(dataset.snapshot());
    }

    public synchronized void save(RtpDatasetSnapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        lastMirrorFailure = null;
        // JSON is authoritative: commit it first. If the process exits before
        // the derived mirror is updated, the next load repairs the CSV.
        saveCanonicalInternal(snapshot);
        try {
            csvExporter.writeMirror(samplesCsvFile, snapshot.allTimeSamples());
        } catch (IOException exception) {
            lastMirrorFailure = exception;
        }
    }

    /** Commits authoritative JSON without touching the independently retried CSV mirror. */
    public synchronized void saveCanonical(RtpDatasetSnapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        saveCanonicalInternal(snapshot);
    }

    /**
     * Rebuilds only the derived CSV mirror. This intentionally leaves the
     * authoritative JSON generation untouched, so a locked CSV cannot cause
     * repeated full-dataset JSON rewrites while the mapper is idle.
     */
    public synchronized void repairCsvMirror(RtpDatasetSnapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        lastMirrorFailure = null;
        try {
            csvExporter.writeMirror(samplesCsvFile, snapshot.allTimeSamples());
        } catch (IOException exception) {
            lastMirrorFailure = exception;
            throw exception;
        }
    }

    public Path export(RtpDatasetSnapshot snapshot, SampleScope scope) throws IOException {
        return csvExporter.export(snapshot, scope);
    }

    public Path exportAllTime(RtpDataset dataset) throws IOException {
        return export(dataset.snapshot(), SampleScope.ALL_TIME);
    }

    public Path exportSession(RtpDataset dataset) throws IOException {
        return export(dataset.snapshot(), SampleScope.SESSION);
    }

    public Path dataDirectory() { return dataDirectory; }
    public Path samplesJsonFile() { return samplesJsonFile; }
    public Path samplesCsvFile() { return samplesCsvFile; }
    public Path exportDirectory() { return csvExporter.exportDirectory(); }

    /** Returns and clears the latest non-fatal CSV mirror failure, if any. */
    public synchronized IOException takeMirrorFailure() {
        IOException failure = lastMirrorFailure;
        lastMirrorFailure = null;
        return failure;
    }

    private void reconcileCsvMirror(List<RtpSample> samples) {
        lastMirrorFailure = null;
        try {
            csvExporter.writeMirror(samplesCsvFile, samples);
        } catch (IOException exception) {
            lastMirrorFailure = exception;
            // JSON is authoritative; a later save/load will retry the mirror.
        }
    }

    private void saveCanonicalInternal(RtpDatasetSnapshot snapshot) throws IOException {
        Files.createDirectories(dataDirectory);
        AtomicFileIO.writeUtf8(samplesJsonFile, encode(snapshot));
    }

    private String encode(RtpDatasetSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("savedAt", clock.millis());
        root.addProperty("allTimeRevision", snapshot.allTimeRevision());
        JsonArray samples = new JsonArray(snapshot.allTimeSamples().size());
        for (RtpSample sample : snapshot.allTimeSamples()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("sample", sample.sampleNumber());
            entry.addProperty("x", sample.x());
            if (sample.hasY()) {
                entry.addProperty("y", sample.y());
            } else {
                entry.add("y", JsonNull.INSTANCE);
            }
            entry.addProperty("z", sample.z());
            entry.addProperty("dimension", sample.dimension());
            entry.addProperty("timestamp", sample.timestamp());
            entry.addProperty("requestedRegion", sample.requestedRegion().id());
            entry.addProperty("category", sample.category().name());
            samples.add(entry);
        }
        root.add("samples", samples);
        return GSON.toJson(root) + System.lineSeparator();
    }

    private static List<RtpSample> parseSamples(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray entries;
        int schemaVersion;
        if (root.isJsonArray()) {
            // Accept the early/legacy array-only layout.
            entries = root.getAsJsonArray();
            schemaVersion = 0;
        } else if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            JsonElement schema = object.get("schemaVersion");
            schemaVersion = schema == null ? 1 : schema.getAsInt();
            if (schemaVersion > SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported RTP sample schema: " + schema);
            }
            JsonElement samples = object.get("samples");
            if (samples == null || !samples.isJsonArray()) {
                throw new IllegalArgumentException("RTP sample JSON has no samples array");
            }
            entries = samples.getAsJsonArray();
        } else {
            throw new IllegalArgumentException("RTP sample JSON root must be an object or array");
        }

        List<RtpSample> result = new ArrayList<>(entries.size());
        Set<Long> numbers = new HashSet<>(Math.max(16, entries.size() * 2));
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Every RTP sample must be a JSON object");
            }
            JsonObject entry = element.getAsJsonObject();
            long sampleNumber = required(entry, "sample").getAsLong();
            if (!numbers.add(sampleNumber)) {
                throw new IllegalArgumentException("Duplicate sample number in storage: " + sampleNumber);
            }
            double y = entry.has("y") && !entry.get("y").isJsonNull()
                    ? entry.get("y").getAsDouble()
                    : RtpSample.MISSING_Y;
            SampleCategory category = entry.has("category")
                    ? SampleCategory.parse(entry.get("category").getAsString())
                    : SampleCategory.DEFAULT;
            // requestedRegion was introduced by schema 2. Never infer or
            // trust provenance accidentally present in a legacy row.
            RtpRegion requestedRegion = schemaVersion >= 2
                    ? readRequestedRegion(entry)
                    : RtpRegion.UNKNOWN;
            result.add(new RtpSample(
                    sampleNumber,
                    required(entry, "x").getAsDouble(),
                    y,
                    required(entry, "z").getAsDouble(),
                    required(entry, "dimension").getAsString(),
                    required(entry, "timestamp").getAsLong(),
                    requestedRegion,
                    category
            ));
        }
        result.sort(Comparator.comparingLong(RtpSample::sampleNumber));
        return List.copyOf(result);
    }

    private static JsonElement required(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("RTP sample is missing '" + key + "'");
        }
        return value;
    }

    private static RtpRegion readRequestedRegion(JsonObject entry) {
        JsonElement value = entry.get("requestedRegion");
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return RtpRegion.UNKNOWN;
        }
        return RtpRegion.parseOrUnknown(value.getAsString());
    }
}
