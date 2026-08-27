package com.donutsmp.rtpmapper.data;

import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void jsonAndCsvRoundTripPreservesPrecisionCategoryAndMissingY() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T18:42:17Z"), ZoneOffset.UTC);
        DataStorage storage = new DataStorage(
                temporaryDirectory.resolve("config/rtpmapper"),
                temporaryDirectory.resolve("rtpmapper/exports"),
                clock);
        RtpDataset dataset = new RtpDataset();
        RtpSample first = dataset.addSample(
                -153284.42123456789, 68.125, 72482.13000000001,
                "minecraft:overworld", 1787281234000L,
                RtpRegion.EU_WEST, SampleCategory.REGION_2);
        dataset.addSample(2.25, RtpSample.MISSING_Y, -9.75,
                "custom:dimension,with-comma", 1787281235000L);

        storage.save(dataset);
        List<RtpSample> loaded = storage.loadSamples();

        assertEquals(2, loaded.size());
        assertEquals(first, loaded.getFirst());
        assertFalse(loaded.get(1).hasY());
        assertTrue(Files.readString(storage.samplesCsvFile()).contains(
                "\"custom:dimension,with-comma\""));
        assertTrue(Files.readString(storage.samplesJsonFile()).contains("REGION_2"));
        assertTrue(Files.readString(storage.samplesJsonFile()).contains("\"requestedRegion\": \"eu_west\""));
        assertTrue(Files.readString(storage.samplesCsvFile()).contains(",eu_west\r\n"));
    }

    @Test
    void schemaOneMissingAndInvalidRegionsMigrateToUnknown() throws Exception {
        DataStorage storage = new DataStorage(temporaryDirectory);
        Files.writeString(storage.samplesJsonFile(), """
                {
                  "schemaVersion": 1,
                  "samples": [
                    {"sample": 1, "x": 1, "y": 64, "z": 2,
                     "dimension": "minecraft:overworld", "timestamp": 3},
                    {"sample": 2, "x": 4, "y": null, "z": 5,
                     "dimension": "minecraft:overworld", "timestamp": 6,
                     "requestedRegion": "east"},
                    {"sample": 3, "x": 7, "y": 70, "z": 8,
                     "dimension": "minecraft:overworld", "timestamp": 9,
                     "requestedRegion": {"invalid": true}}
                  ]
                }
                """);

        List<RtpSample> loaded = storage.loadSamples();

        assertEquals(List.of(RtpRegion.UNKNOWN, RtpRegion.UNKNOWN, RtpRegion.UNKNOWN),
                loaded.stream().map(RtpSample::requestedRegion).toList());
        assertTrue(Files.readString(storage.samplesCsvFile()).contains(",unknown\r\n"));
    }

    @Test
    void legacyArrayNeverInventsRegionProvenance() throws Exception {
        DataStorage storage = new DataStorage(temporaryDirectory);
        Files.writeString(storage.samplesJsonFile(), """
                [
                  {"sample": 1, "x": 1, "y": 64, "z": 2,
                   "dimension": "minecraft:overworld", "timestamp": 3,
                   "requestedRegion": "asia"}
                ]
                """);

        assertEquals(RtpRegion.UNKNOWN, storage.loadSamples().getFirst().requestedRegion());
    }

    @Test
    void validJournalRecoversACorruptPrimaryFile() throws Exception {
        DataStorage storage = new DataStorage(temporaryDirectory);
        RtpDataset dataset = new RtpDataset();
        dataset.addSample(1, 2, 3, "minecraft:overworld", 4);
        storage.save(dataset);
        Files.copy(storage.samplesJsonFile(), AtomicFileIO.journalPath(storage.samplesJsonFile()),
                StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(storage.samplesJsonFile(), "{broken");

        List<RtpSample> recovered = storage.loadSamples();

        assertEquals(1, recovered.size());
        assertFalse(Files.exists(AtomicFileIO.journalPath(storage.samplesJsonFile())));
        assertTrue(Files.readString(storage.samplesJsonFile()).contains("minecraft:overworld"));
    }

    @Test
    void validPrimaryWinsOverStaleJournalAndRepairsCsvMirror() throws Exception {
        DataStorage storage = new DataStorage(temporaryDirectory);
        RtpDataset dataset = new RtpDataset();
        dataset.addSample(1, 2, 3, "minecraft:overworld", 4);
        storage.save(dataset);
        String oldGeneration = Files.readString(storage.samplesJsonFile());

        dataset.addSample(10, 20, 30, "minecraft:the_nether", 40);
        storage.save(dataset);
        Files.writeString(AtomicFileIO.journalPath(storage.samplesJsonFile()), oldGeneration);
        Files.writeString(storage.samplesCsvFile(), "stale\r\n");

        List<RtpSample> loaded = storage.loadSamples();

        assertEquals(2, loaded.size());
        assertEquals("minecraft:the_nether", loaded.getLast().dimension());
        assertFalse(Files.exists(AtomicFileIO.journalPath(storage.samplesJsonFile())));
        assertTrue(Files.readString(storage.samplesCsvFile()).contains("minecraft:the_nether"));
    }

    @Test
    void csvMirrorFailureDoesNotPreventCanonicalJsonSave() throws Exception {
        DataStorage storage = new DataStorage(temporaryDirectory);
        Files.createDirectories(storage.samplesCsvFile());
        RtpDataset dataset = new RtpDataset();
        dataset.addSample(1, 2, 3, "minecraft:overworld", 4);

        storage.save(dataset);

        assertTrue(Files.exists(storage.samplesJsonFile()));
        assertEquals(1, storage.loadSamples().size());
        assertTrue(storage.takeMirrorFailure() != null);

        dataset.addSample(10, 20, 30, "minecraft:the_nether", 40);
        storage.saveCanonical(dataset.snapshot());
        assertTrue(Files.readString(storage.samplesJsonFile()).contains("minecraft:the_nether"));
        assertTrue(Files.isDirectory(storage.samplesCsvFile()));
        assertTrue(storage.takeMirrorFailure() == null);

        String canonicalBeforeRepair = Files.readString(storage.samplesJsonFile());
        FileTime canonicalTimestamp = FileTime.fromMillis(1_234_567_890L);
        Files.setLastModifiedTime(storage.samplesJsonFile(), canonicalTimestamp);
        Files.delete(storage.samplesCsvFile());
        storage.repairCsvMirror(dataset.snapshot());

        assertEquals(canonicalBeforeRepair, Files.readString(storage.samplesJsonFile()));
        assertEquals(canonicalTimestamp, Files.getLastModifiedTime(storage.samplesJsonFile()));
        assertTrue(Files.readString(storage.samplesCsvFile()).contains("minecraft:the_nether"));
        assertTrue(storage.takeMirrorFailure() == null);
    }

    @Test
    void exportUsesTimestampAndNeverOverwritesAnExistingExport() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T18:42:17Z"), ZoneOffset.UTC);
        CsvExporter exporter = new CsvExporter(temporaryDirectory, clock);
        List<RtpSample> samples = List.of(
                new RtpSample(1, 3, 4, 5, "minecraft:overworld", 6));

        Path first = exporter.export(samples);
        Path second = exporter.export(samples);

        assertEquals("rtp_data_2026-08-20_18-42-17.csv", first.getFileName().toString());
        assertEquals("rtp_data_2026-08-20_18-42-17_2.csv", second.getFileName().toString());
        String csv = Files.readString(first);
        assertTrue(csv.startsWith(CsvExporter.HEADER + "\r\n"));
        assertTrue(csv.endsWith("\r\n"));
    }
}
