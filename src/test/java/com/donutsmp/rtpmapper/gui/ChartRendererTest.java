package com.donutsmp.rtpmapper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.donutsmp.rtpmapper.util.CoordinateTransform;
import org.junit.jupiter.api.Test;

class ChartRendererTest {
    @Test
    void hundredThousandSamplesAreBinnedAndRemainHoverable() {
        int count = 100_000;
        double[] x = new double[count];
        double[] z = new double[count];
        for (int index = 1; index < count; index++) {
            x[index] = ((index % 1_000) - 500) * 800.0;
            z[index] = ((index / 1_000) - 50) * 800.0;
        }
        ChartPointProvider provider = provider(x, z);
        CoordinateTransform transform = new CoordinateTransform(0.0, 0.0, 1_000.0);
        transform.setBounds(0, 0, 1_000, 1_000);
        ChartRenderer renderer = new ChartRenderer();

        int hovered = renderer.findHoveredIndex(provider, transform, 500.0, 500.0);

        assertEquals(0, hovered);
        assertTrue(renderer.renderedGlyphCount() <= ChartRenderer.MAX_RENDERED_GLYPHS);
        assertTrue(renderer.renderedGlyphCount() > 0);
        assertTrue(renderer.lastHoverCandidateChecks() < 1_000);
        assertTrue(renderer.hoverIndexCapacity() <= 262_144);
    }

    @Test
    void coincidentHundredThousandPointHoverChecksOneRepresentative() {
        int count = 100_000;
        ChartPointProvider provider = provider(new double[count], new double[count]);
        CoordinateTransform transform = new CoordinateTransform(0.0, 0.0, 100.0);
        transform.setBounds(0, 0, 800, 600);
        ChartRenderer renderer = new ChartRenderer();

        assertEquals(0, renderer.findHoveredIndex(provider, transform, 400.0, 300.0));
        assertEquals(1, renderer.lastHoverCandidateChecks());
    }

    @Test
    void coincidentPointsBelowDenseThresholdRenderOnce() {
        int count = ChartRenderer.MAX_RENDERED_GLYPHS - 1;
        ChartPointProvider provider = provider(new double[count], new double[count]);
        CoordinateTransform transform = new CoordinateTransform(0.0, 0.0, 100.0);
        transform.setBounds(0, 0, 800, 600);
        ChartRenderer renderer = new ChartRenderer();

        renderer.findHoveredIndex(provider, transform, 400.0, 300.0);

        assertEquals(1, renderer.renderedGlyphCount());
    }

    @Test
    void emptyFourKChartDoesNotAllocateAPerPixelHoverIndex() {
        ChartPointProvider provider = provider(new double[0], new double[0]);
        CoordinateTransform transform = new CoordinateTransform();
        transform.setBounds(0, 0, 3_840, 2_160);
        ChartRenderer renderer = new ChartRenderer();

        renderer.findHoveredIndex(provider, transform, 1_920.0, 1_080.0);

        assertEquals(0, renderer.hoverIndexCapacity());
    }

    @Test
    void farPannedRingRetainsAVisibleArcAndLabelAnchor() {
        double radius = 1_000_000.0;
        double tangentAngle = Math.toRadians(17.0);
        double centerX = 500.0 - Math.cos(tangentAngle) * radius;
        double centerY = 500.0 - Math.sin(tangentAngle) * radius;
        ChartRenderer.VisibleArcSet visibleArcs = new ChartRenderer.VisibleArcSet();

        ChartRenderer.computeVisibleArcs(
                centerX,
                centerY,
                radius,
                0,
                0,
                1_000,
                1_000,
                visibleArcs
        );

        assertTrue(visibleArcs.arcCount > 0);
        double visibleLength = 0.0;
        for (int arc = 0; arc < visibleArcs.arcCount; arc++) {
            visibleLength += radius * (visibleArcs.ends[arc] - visibleArcs.starts[arc]);
        }
        assertTrue(visibleLength > 500.0);
        double labelAngle = ChartRenderer.selectVisibleLabelAngle(
                centerX,
                centerY,
                radius,
                0,
                0,
                1_000,
                1_000,
                visibleArcs
        );
        assertTrue(Double.isFinite(labelAngle));
        double labelX = centerX + Math.cos(labelAngle) * radius;
        double labelY = centerY + Math.sin(labelAngle) * radius;
        assertTrue(labelX >= 0.0 && labelX <= 999.0);
        assertTrue(labelY >= 0.0 && labelY <= 999.0);
    }

    @Test
    void tangentRingStillProvidesASingleVisibleLabelAnchor() {
        ChartRenderer.VisibleArcSet visibleArcs = new ChartRenderer.VisibleArcSet();
        ChartRenderer.computeVisibleArcs(
                -1_000.0,
                500.0,
                1_000.0,
                0,
                0,
                1_000,
                1_000,
                visibleArcs
        );

        assertEquals(0, visibleArcs.arcCount);
        assertTrue(Double.isFinite(ChartRenderer.selectVisibleLabelAngle(
                -1_000.0,
                500.0,
                1_000.0,
                0,
                0,
                1_000,
                1_000,
                visibleArcs
        )));
    }

    private static ChartPointProvider provider(double[] x, double[] z) {
        return new ChartPointProvider() {
            @Override
            public int size() {
                return x.length;
            }

            @Override
            public long revision() {
                return 1;
            }

            @Override
            public long sampleNumberAt(int index) {
                return index + 1L;
            }

            @Override
            public double xAt(int index) {
                return x[index];
            }

            @Override
            public double yAt(int index) {
                return 64.0;
            }

            @Override
            public double zAt(int index) {
                return z[index];
            }

            @Override
            public long timestampAt(int index) {
                return 0;
            }

            @Override
            public String dimensionAt(int index) {
                return "minecraft:overworld";
            }

            @Override
            public String categoryAt(int index) {
                return "DEFAULT";
            }
        };
    }
}
