package com.donutsmp.rtpmapper.gui;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class StatisticsPanel {
    private static final int COMPACT_CONTENT_HEIGHT = 185;
    private static final int TEXT = 0xFFD7DEE8;
    private static final int MUTED = 0xFF8793A5;
    private static final int ACCENT = 0xFF57D3FF;

    public void render(
        GuiGraphics graphics,
        Font font,
        MapperStatisticsView stats,
        int x,
        int y,
        int width,
        int height,
        int bucketScroll
    ) {
        graphics.fill(x, y, x + width, y + height, 0xB8171B24);
        graphics.renderOutline(x, y, width, height, 0xFF303846);
        graphics.drawString(font, "STATISTICS", x + 8, y + 7, ACCENT, false);

        if (height < 36) {
            return;
        }

        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        try {
        if (stats.totalSamples() == 0) {
            graphics.drawString(font, "No samples in this view", x + 8, y + 24, MUTED, false);
            return;
        }

        List<MapperStatisticsView.RegionCountView> regionCounts = visibleRegionCounts(stats);
        boolean compactContent = height < COMPACT_CONTENT_HEIGHT;
        int lineY = y + 23;
        line(graphics, font, x, lineY, width, "Mean X / Z", compact(stats.averageX()) + " / " + compact(stats.averageZ()));
        lineY += 11;
        line(graphics, font, x, lineY, width, "X range", compact(stats.minimumX()) + " .. " + compact(stats.maximumX()));
        lineY += 11;
        line(graphics, font, x, lineY, width, "Z range", compact(stats.minimumZ()) + " .. " + compact(stats.maximumZ()));
        lineY += 11;
        if (!compactContent) {
            line(graphics, font, x, lineY, width, "Mean radius", compact(stats.averageDistance()));
            lineY += 11;
        }
        line(graphics, font, x, lineY, width, "Radius range", compact(stats.minimumDistance()) + " .. " + compact(stats.maximumDistance()));
        lineY += 14;

        if (!compactContent) {
            graphics.drawString(font, "QUADRANTS", x + 8, lineY, ACCENT, false);
            lineY += 11;
        }
        graphics.drawString(
            font,
            fit(font, String.format(Locale.ROOT, "NE %.1f%%   NW %.1f%%", stats.northEastPercent(), stats.northWestPercent()), width - 16),
            x + 8,
            lineY,
            TEXT,
            false
        );
        lineY += 11;
        graphics.drawString(
            font,
            fit(font, String.format(Locale.ROOT, "SE %.1f%%   SW %.1f%%", stats.southEastPercent(), stats.southWestPercent()), width - 16),
            x + 8,
            lineY,
            TEXT,
            false
        );
        lineY += 14;

        if (!compactContent) {
            graphics.drawString(font, fit(font, "REQUESTED REGIONS", width - 16), x + 8, lineY, ACCENT, false);
            lineY += 11;
        }
        for (int index = 0; index < regionCounts.size(); index += 2) {
            MapperStatisticsView.RegionCountView first = regionCounts.get(index);
            MapperStatisticsView.RegionCountView second = index + 1 < regionCounts.size()
                ? regionCounts.get(index + 1)
                : null;
            drawRegionRow(graphics, font, x, lineY, width, first, second);
            lineY += 10;
        }
        lineY += 3;

        if (visibleBucketRows(stats, height) > 0) {
            graphics.drawString(font, fit(font, "RADIAL BUCKETS · WHEEL", width - 16), x + 8, lineY, ACCENT, false);
            lineY += 11;
        } else {
            return;
        }
        int firstBucket = Math.clamp(bucketScroll, 0, maximumBucketScroll(stats, height));
        for (int bucketIndex = firstBucket; bucketIndex < stats.radialBuckets().size(); bucketIndex++) {
            MapperStatisticsView.RadialBucketView bucket = stats.radialBuckets().get(bucketIndex);
            if (lineY + 9 > y + height - 4) {
                break;
            }
            String range = compactBucket(bucket.minimumInclusive()) + "-" + compactBucket(bucket.maximumExclusive());
            line(graphics, font, x, lineY, width, range, Integer.toString(bucket.count()));
            lineY += 10;
        }
        } finally {
            graphics.disableScissor();
        }
    }

    public int maximumBucketScroll(MapperStatisticsView stats, int height) {
        return Math.max(0, stats.radialBuckets().size() - visibleBucketRows(stats, height));
    }

    public int visibleBucketRows(MapperStatisticsView stats, int height) {
        int regionRows = (visibleRegionCounts(stats).size() + 1) / 2;
        int fixedHeight = (height < COMPACT_CONTENT_HEIGHT ? 112 : 145) + regionRows * 10;
        return Math.max(0, (height - fixedHeight) / 10);
    }

    private static void line(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value) {
        int valueX = x + Math.min(92, Math.max(64, width / 2));
        graphics.drawString(font, fit(font, label, Math.max(8, valueX - x - 12)), x + 8, y, MUTED, false);
        graphics.drawString(font, fit(font, value, Math.max(8, x + width - 8 - valueX)), valueX, y, TEXT, false);
    }

    private static void drawRegionRow(
        GuiGraphics graphics,
        Font font,
        int x,
        int y,
        int width,
        MapperStatisticsView.RegionCountView first,
        MapperStatisticsView.RegionCountView second
    ) {
        int gap = 4;
        int columnWidth = Math.max(8, (width - 16 - gap) / 2);
        drawRegionEntry(graphics, font, x + 8, y, columnWidth, first);
        if (second != null) {
            drawRegionEntry(graphics, font, x + 8 + columnWidth + gap, y, columnWidth, second);
        }
    }

    private static void drawRegionEntry(
        GuiGraphics graphics,
        Font font,
        int x,
        int y,
        int width,
        MapperStatisticsView.RegionCountView entry
    ) {
        String label = entry.region().shortName() + " " + formatCount(entry.count());
        graphics.drawString(font, fit(font, label, width), x, y, entry.region().colorArgb(), false);
    }

    private static List<MapperStatisticsView.RegionCountView> visibleRegionCounts(MapperStatisticsView stats) {
        return stats.requestedRegionCounts().stream()
            .filter(entry -> entry.region().selectable() || entry.count() > 0)
            .toList();
    }

    private static String fit(Font font, String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) {
            return value;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(1, maximumWidth - font.width(ellipsis))) + ellipsis;
    }

    private static String compact(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000.0) {
            return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
        }
        if (absolute >= 1_000.0) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String compactBucket(double value) {
        if (value >= 1_000_000.0) {
            return String.format(Locale.ROOT, "%.0fM", value / 1_000_000.0);
        }
        if (value >= 1_000.0) {
            return String.format(Locale.ROOT, "%.0fk", value / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String formatCount(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
