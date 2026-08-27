package com.donutsmp.rtpmapper.data;

import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExporterTest {
    @Test
    void escapingFollowsRfc4180() {
        assertEquals("plain", CsvExporter.escape("plain"));
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"));
        assertEquals("\"a\"\"b\"", CsvExporter.escape("a\"b"));
        assertEquals("\"a\r\nb\"", CsvExporter.escape("a\r\nb"));
    }

    @Test
    void missingYIsAnEmptyFieldAndDistanceIsCalculatedFromXZ() {
        String csv = CsvExporter.toCsv(List.of(
                new RtpSample(1, 3.0, RtpSample.MISSING_Y, 4.0,
                        "minecraft:overworld", 10, RtpRegion.ASIA)));

        assertEquals(
                "sample,x,y,z,distance_from_origin,dimension,timestamp,requested_region\r\n"
                        + "1,3.0,,4.0,5.0,minecraft:overworld,10,asia\r\n",
                csv
        );
    }
}
