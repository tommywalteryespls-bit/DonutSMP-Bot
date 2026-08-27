package com.donutsmp.rtpmapper.data;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/** Produces the canonical RFC 4180 CSV mirror and user-requested exports. */
public final class CsvExporter {
    public static final String HEADER =
            "sample,x,y,z,distance_from_origin,dimension,timestamp,requested_region";

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path exportDirectory;
    private final Clock clock;

    public CsvExporter() {
        this(FabricLoader.getInstance().getGameDir().resolve("rtpmapper").resolve("exports"));
    }

    public CsvExporter(Path exportDirectory) {
        this(exportDirectory, Clock.systemDefaultZone());
    }

    public CsvExporter(Path exportDirectory, Clock clock) {
        this.exportDirectory = exportDirectory.toAbsolutePath();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Path export(List<RtpSample> samples) throws IOException {
        Objects.requireNonNull(samples, "samples");
        Files.createDirectories(exportDirectory);
        String timestamp = FILE_TIMESTAMP.withZone(clock.getZone()).format(clock.instant());
        Path destination = uniqueExportPath("rtp_data_" + timestamp);
        AtomicFileIO.writeNew(destination, toCsv(samples).getBytes(StandardCharsets.UTF_8));
        return destination;
    }

    public Path export(RtpDatasetSnapshot snapshot, SampleScope scope) throws IOException {
        return export(snapshot.samples(scope));
    }

    public void writeMirror(Path destination, List<RtpSample> samples) throws IOException {
        AtomicFileIO.writeUtf8(destination, toCsv(samples));
    }

    public static String toCsv(List<RtpSample> samples) {
        Objects.requireNonNull(samples, "samples");
        StringBuilder output = new StringBuilder(Math.max(128, samples.size() * 96));
        output.append(HEADER).append("\r\n");
        for (RtpSample sample : samples) {
            output.append(sample.sampleNumber()).append(',')
                    .append(Double.toString(sample.x())).append(',');
            if (sample.hasY()) {
                output.append(Double.toString(sample.y()));
            }
            output.append(',')
                    .append(Double.toString(sample.z())).append(',')
                    .append(Double.toString(sample.distanceFromOrigin())).append(',')
                    .append(escape(sample.dimension())).append(',')
                    .append(sample.timestamp()).append(',')
                    .append(escape(sample.requestedRegion().id()))
                    .append("\r\n");
        }
        return output.toString();
    }

    public static String escape(String value) {
        Objects.requireNonNull(value, "value");
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public Path exportDirectory() {
        return exportDirectory;
    }

    private Path uniqueExportPath(String stem) {
        Path candidate = exportDirectory.resolve(stem + ".csv");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = exportDirectory.resolve(stem + "_" + suffix++ + ".csv");
        }
        return candidate;
    }
}
