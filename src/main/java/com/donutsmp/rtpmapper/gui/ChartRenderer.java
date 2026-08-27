package com.donutsmp.rtpmapper.gui;

import com.donutsmp.rtpmapper.region.RtpRegion;
import com.donutsmp.rtpmapper.util.CoordinateTransform;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders the mapper's X/Z scatter chart without retaining application data
 * objects. Projection and hover acceleration are rebuilt only when the
 * provider, transform, or plot bounds change.
 */
public final class ChartRenderer {
    /** Maximum number of point glyphs submitted in one frame. */
    public static final int MAX_RENDERED_GLYPHS = 4_000;

    /** Default center-to-center hover distance in GUI pixels. */
    public static final double DEFAULT_HOVER_RADIUS = 6.0;

    private static final int OUTSIDE = Integer.MIN_VALUE;
    private static final int HOVER_BUCKET_SIZE = 12;
    private static final int MAX_RING_COUNT = 16;
    private static final int MAX_CIRCLE_SEGMENTS = 240;
    private static final int MIN_CIRCLE_SEGMENTS = 36;
    private static final int MAX_ARC_BOUNDARIES = 10;
    private static final int MAX_VISIBLE_ARCS = MAX_ARC_BOUNDARIES - 1;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double TARGET_RING_SAMPLE_SPACING = 2.0;
    private static final double ARC_ANGLE_EPSILON = 1.0e-12;
    private static final double PREFERRED_RING_LABEL_ANGLE = TWO_PI - Math.PI / 4.0;

    private static final int BACKGROUND_COLOR = 0xFF0A1018;
    private static final int BORDER_COLOR = 0xFF536273;
    private static final int GRID_COLOR = 0x382A4257;
    private static final int GRID_LABEL_COLOR = 0xFF718294;
    private static final int RING_COLOR = 0x664B6B86;
    private static final int RING_LABEL_COLOR = 0xFFA6B7C8;
    private static final int LABEL_BACKGROUND_COLOR = 0xC20A1018;
    private static final int AXIS_COLOR = 0xB8C6D1DC;
    private static final int ORIGIN_COLOR = 0xFFFFD166;
    private static final int HOVER_COLOR = 0xFFFFFFFF;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());

    private ChartPointProvider cachedProvider;
    private long cachedProviderRevision = Long.MIN_VALUE;
    private long cachedTransformRevision = Long.MIN_VALUE;
    private int cachedProviderSize = -1;
    private int cachedLeft;
    private int cachedTop;
    private int cachedWidth;
    private int cachedHeight;

    private int[] projectedX = new int[0];
    private int[] projectedY = new int[0];
    private int[] nextInHoverBucket = new int[0];
    private int[] hoverBucketHeads = new int[0];
    private int[] hoverPixelKeys = new int[0];
    private int[] hoverPixelOrdinals = new int[0];
    private int[] hoverHashGeneration = new int[0];
    private int hoverHashMask;
    private int hoverGeneration;
    private int hoverBucketColumns;
    private int hoverBucketRows;
    private int lastHoverCandidateChecks;

    private int[] uniquePixelIndices = new int[0];
    private int[] uniquePixelDensity = new int[0];

    private int[] renderedIndices = new int[0];
    private int[] renderedX = new int[0];
    private int[] renderedY = new int[0];
    private int[] renderedDensity = new int[0];
    private int[] renderedColors = new int[0];
    private int renderedCount;

    private int[] binRepresentative = new int[0];
    private int[] binDensity = new int[0];
    private final VisibleArcSet visibleRingArcs = new VisibleArcSet();

    /** Fixed-capacity scratch layout shared by ring drawing and label placement. */
    static final class VisibleArcSet {
        final double[] boundaries = new double[MAX_ARC_BOUNDARIES];
        final double[] starts = new double[MAX_VISIBLE_ARCS];
        final double[] ends = new double[MAX_VISIBLE_ARCS];
        int boundaryCount;
        int arcCount;
    }

    /**
     * Draws the complete chart and schedules a tooltip for the nearest point.
     * The supplied bounds also become the transform's current screen bounds.
     */
    public void render(
            GuiGraphics graphics,
            Font font,
            ChartPointProvider provider,
            CoordinateTransform transform,
            int left,
            int top,
            int width,
            int height,
            int mouseX,
            int mouseY,
            double pointSize,
            boolean showGrid,
            boolean showDistanceRings
    ) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(transform, "transform");

        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int right = endCoordinate(left, safeWidth);
        int bottom = endCoordinate(top, safeHeight);

        ensureCache(provider, transform, left, top, safeWidth, safeHeight);

        graphics.fill(left, top, right, bottom, BACKGROUND_COLOR);
        int hoveredIndex = findHoveredIndexFromCache(mouseX, mouseY, DEFAULT_HOVER_RADIUS);

        graphics.enableScissor(left, top, right, bottom);
        try {
            if (showGrid) {
                drawGrid(graphics, font, transform, left, top, right, bottom);
            }
            if (showDistanceRings) {
                drawDistanceRings(graphics, font, transform, left, top, right, bottom);
            }
            drawAxesAndOrigin(graphics, font, transform, left, top, right, bottom);
            drawPoints(graphics, normalizedPointSize(pointSize));
            if (hoveredIndex >= 0) {
                drawHoverMarker(graphics, hoveredIndex);
            }
        } finally {
            graphics.disableScissor();
        }

        drawBorder(graphics, left, top, right, bottom);
        if (hoveredIndex >= 0) {
            graphics.setComponentTooltipForNextFrame(
                    font,
                    tooltipFor(provider, hoveredIndex),
                    mouseX,
                    mouseY
            );
        }
    }

    /** Draws with a two-pixel point size and both reference layers enabled. */
    public void render(
            GuiGraphics graphics,
            Font font,
            ChartPointProvider provider,
            CoordinateTransform transform,
            int left,
            int top,
            int width,
            int height,
            int mouseX,
            int mouseY
    ) {
        render(
                graphics,
                font,
                provider,
                transform,
                left,
                top,
                width,
                height,
                mouseX,
                mouseY,
                2.0,
                true,
                true
        );
    }

    /** Draws using bounds already stored by {@code transform}. */
    public void render(
            GuiGraphics graphics,
            Font font,
            ChartPointProvider provider,
            CoordinateTransform transform,
            int mouseX,
            int mouseY,
            double pointSize,
            boolean showGrid,
            boolean showDistanceRings
    ) {
        render(
                graphics,
                font,
                provider,
                transform,
                transform.left(),
                transform.top(),
                transform.width(),
                transform.height(),
                mouseX,
                mouseY,
                pointSize,
                showGrid,
                showDistanceRings
        );
    }

    /** Draws with defaults using bounds already stored by {@code transform}. */
    public void render(
            GuiGraphics graphics,
            Font font,
            ChartPointProvider provider,
            CoordinateTransform transform,
            int mouseX,
            int mouseY
    ) {
        render(
                graphics,
                font,
                provider,
                transform,
                mouseX,
                mouseY,
                2.0,
                true,
                true
        );
    }

    /**
     * Returns the provider index nearest to the mouse within the default hover
     * radius, or {@code -1}. The lookup uses all visible points, including
     * points omitted from the glyph-density cache.
     */
    public int findHoveredIndex(
            ChartPointProvider provider,
            CoordinateTransform transform,
            int left,
            int top,
            int width,
            int height,
            double mouseX,
            double mouseY
    ) {
        return findHoveredIndex(
                provider,
                transform,
                left,
                top,
                width,
                height,
                mouseX,
                mouseY,
                DEFAULT_HOVER_RADIUS
        );
    }

    /**
     * Returns the provider index nearest to the mouse within
     * {@code hoverRadius}, or {@code -1}.
     */
    public int findHoveredIndex(
            ChartPointProvider provider,
            CoordinateTransform transform,
            int left,
            int top,
            int width,
            int height,
            double mouseX,
            double mouseY,
            double hoverRadius
    ) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(transform, "transform");
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        ensureCache(provider, transform, left, top, safeWidth, safeHeight);
        return findHoveredIndexFromCache(mouseX, mouseY, hoverRadius);
    }

    /** Looks up a hovered point using bounds already stored by the transform. */
    public int findHoveredIndex(
            ChartPointProvider provider,
            CoordinateTransform transform,
            double mouseX,
            double mouseY,
            double hoverRadius
    ) {
        return findHoveredIndex(
                provider,
                transform,
                transform.left(),
                transform.top(),
                transform.width(),
                transform.height(),
                mouseX,
                mouseY,
                hoverRadius
        );
    }

    /** Uses transform bounds and {@link #DEFAULT_HOVER_RADIUS}. */
    public int findHoveredIndex(
            ChartPointProvider provider,
            CoordinateTransform transform,
            double mouseX,
            double mouseY
    ) {
        return findHoveredIndex(
                provider,
                transform,
                mouseX,
                mouseY,
                DEFAULT_HOVER_RADIUS
        );
    }

    /** Invalidates all projection and spatial caches. */
    public void invalidate() {
        cachedProvider = null;
        cachedProviderRevision = Long.MIN_VALUE;
        cachedTransformRevision = Long.MIN_VALUE;
        cachedProviderSize = -1;
        renderedCount = 0;
    }

    /** Returns the number of glyphs retained for the current cached view. */
    public int renderedGlyphCount() {
        return renderedCount;
    }

    /** Number of representative points inspected by the most recent hover query. */
    public int lastHoverCandidateChecks() {
        return lastHoverCandidateChecks;
    }

    int hoverIndexCapacity() {
        return hoverHashGeneration.length;
    }

    private void ensureCache(
            ChartPointProvider provider,
            CoordinateTransform transform,
            int left,
            int top,
            int width,
            int height
    ) {
        transform.setBounds(left, top, width, height);

        int providerSize = provider.size();
        if (providerSize < 0) {
            throw new IllegalArgumentException("ChartPointProvider.size() must be non-negative");
        }

        long providerRevision = provider.revision();
        long transformRevision = transform.revision();
        if (cachedProvider == provider
                && cachedProviderRevision == providerRevision
                && cachedTransformRevision == transformRevision
                && cachedProviderSize == providerSize
                && cachedLeft == left
                && cachedTop == top
                && cachedWidth == width
                && cachedHeight == height) {
            return;
        }

        ensureProjectionCapacity(providerSize);
        Arrays.fill(projectedX, 0, providerSize, OUTSIDE);
        Arrays.fill(nextInHoverBucket, 0, providerSize, -1);

        hoverBucketColumns = positiveCeilingDivision(width, HOVER_BUCKET_SIZE);
        hoverBucketRows = positiveCeilingDivision(height, HOVER_BUCKET_SIZE);
        int bucketCount = checkedCellCount(hoverBucketColumns, hoverBucketRows);
        if (hoverBucketHeads.length < bucketCount) {
            hoverBucketHeads = new int[bucketCount];
        }
        Arrays.fill(hoverBucketHeads, 0, bucketCount, -1);

        int pixelCount = checkedCellCount(width, height);
        ensureHoverHashCapacity(Math.min(providerSize, pixelCount));
        int currentHoverGeneration = nextHoverGeneration();

        int denseCellSize = Math.max(
                1,
                (int) Math.ceil(Math.sqrt((long) width * height / (double) MAX_RENDERED_GLYPHS))
        );
        int denseBinColumns = positiveCeilingDivision(width, denseCellSize);
        int denseBinRows = positiveCeilingDivision(height, denseCellSize);
        while ((long) denseBinColumns * denseBinRows > MAX_RENDERED_GLYPHS) {
            denseCellSize++;
            denseBinColumns = positiveCeilingDivision(width, denseCellSize);
            denseBinRows = positiveCeilingDivision(height, denseCellSize);
        }
        int denseBinCount = checkedCellCount(denseBinColumns, denseBinRows);
        ensureBinCapacity(denseBinCount);
        Arrays.fill(binRepresentative, 0, denseBinCount, -1);
        Arrays.fill(binDensity, 0, denseBinCount, 0);
        ensureUniquePixelCapacity(Math.min(providerSize, MAX_RENDERED_GLYPHS));

        int uniquePixelCount = 0;
        int right = endCoordinate(left, width);
        int bottom = endCoordinate(top, height);
        for (int index = 0; index < providerSize; index++) {
            double screenX = transform.worldToScreenX(provider.xAt(index));
            double screenY = transform.worldToScreenZ(provider.zAt(index));
            if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
                continue;
            }

            int pixelX = roundedCoordinate(screenX);
            int pixelY = roundedCoordinate(screenY);
            if (pixelX == OUTSIDE || pixelY == OUTSIDE
                    || pixelX < left || pixelX >= right
                    || pixelY < top || pixelY >= bottom) {
                continue;
            }

            projectedX[index] = pixelX;
            projectedY[index] = pixelY;
            int localX = pixelX - left;
            int localY = pixelY - top;
            int hoverPixel = localY * width + localX;
            int hoverSlot = mixedHash(hoverPixel) & hoverHashMask;
            while (hoverHashGeneration[hoverSlot] == currentHoverGeneration
                    && hoverPixelKeys[hoverSlot] != hoverPixel) {
                hoverSlot = (hoverSlot + 1) & hoverHashMask;
            }
            if (hoverHashGeneration[hoverSlot] != currentHoverGeneration) {
                // Iteration is sample-number order, so the first point at a
                // rounded pixel is a stable representative for every point
                // hidden below it. Hover cost is consequently bounded by
                // screen pixels rather than dataset density.
                hoverHashGeneration[hoverSlot] = currentHoverGeneration;
                hoverPixelKeys[hoverSlot] = hoverPixel;
                hoverPixelOrdinals[hoverSlot] = uniquePixelCount;
                if (uniquePixelCount < MAX_RENDERED_GLYPHS) {
                    uniquePixelIndices[uniquePixelCount] = index;
                    uniquePixelDensity[uniquePixelCount] = 1;
                }
                uniquePixelCount++;
                int bucketX = localX / HOVER_BUCKET_SIZE;
                int bucketY = localY / HOVER_BUCKET_SIZE;
                int bucket = bucketY * hoverBucketColumns + bucketX;
                nextInHoverBucket[index] = hoverBucketHeads[bucket];
                hoverBucketHeads[bucket] = index;
            } else {
                int ordinal = hoverPixelOrdinals[hoverSlot];
                if (ordinal < MAX_RENDERED_GLYPHS) {
                    uniquePixelDensity[ordinal]++;
                }
            }

            int denseBin = (localY / denseCellSize) * denseBinColumns + (localX / denseCellSize);
            if (binRepresentative[denseBin] < 0) {
                binRepresentative[denseBin] = index;
            }
            binDensity[denseBin]++;
        }

        buildRenderedPoints(provider, uniquePixelCount, denseBinCount);

        cachedProvider = provider;
        cachedProviderRevision = providerRevision;
        cachedTransformRevision = transformRevision;
        cachedProviderSize = providerSize;
        cachedLeft = left;
        cachedTop = top;
        cachedWidth = width;
        cachedHeight = height;
    }

    private void buildRenderedPoints(
            ChartPointProvider provider,
            int uniquePixelCount,
            int denseBinCount
    ) {
        if (uniquePixelCount <= MAX_RENDERED_GLYPHS) {
            ensureRenderedCapacity(uniquePixelCount);
            renderedCount = 0;
            for (int ordinal = 0; ordinal < uniquePixelCount; ordinal++) {
                int index = uniquePixelIndices[ordinal];
                appendRenderedPoint(
                        provider,
                        index,
                        projectedX[index],
                        projectedY[index],
                        uniquePixelDensity[ordinal]
                );
            }
            return;
        }

        ensureRenderedCapacity(Math.min(denseBinCount, MAX_RENDERED_GLYPHS));
        renderedCount = 0;
        for (int bin = 0; bin < denseBinCount; bin++) {
            int representative = binRepresentative[bin];
            if (representative < 0) {
                continue;
            }
            appendRenderedPoint(
                    provider,
                    representative,
                    projectedX[representative],
                    projectedY[representative],
                    binDensity[bin]
            );
        }
    }

    private void appendRenderedPoint(
            ChartPointProvider provider,
            int index,
            int screenX,
            int screenY,
            int density
    ) {
        renderedIndices[renderedCount] = index;
        renderedX[renderedCount] = screenX;
        renderedY[renderedCount] = screenY;
        renderedDensity[renderedCount] = density;
        renderedColors[renderedCount] = colorForRequestedRegion(provider.requestedRegionAt(index));
        renderedCount++;
    }

    private int findHoveredIndexFromCache(double mouseX, double mouseY, double radius) {
        lastHoverCandidateChecks = 0;
        if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY)
                || !Double.isFinite(radius) || radius < 0.0) {
            return -1;
        }

        double right = (double) cachedLeft + cachedWidth;
        double bottom = (double) cachedTop + cachedHeight;
        if (mouseX < cachedLeft || mouseX >= right
                || mouseY < cachedTop || mouseY >= bottom) {
            return -1;
        }

        int minimumBucketX = clamp(
                (int) Math.floor((mouseX - radius - cachedLeft) / HOVER_BUCKET_SIZE),
                0,
                hoverBucketColumns - 1
        );
        int maximumBucketX = clamp(
                (int) Math.floor((mouseX + radius - cachedLeft) / HOVER_BUCKET_SIZE),
                0,
                hoverBucketColumns - 1
        );
        int minimumBucketY = clamp(
                (int) Math.floor((mouseY - radius - cachedTop) / HOVER_BUCKET_SIZE),
                0,
                hoverBucketRows - 1
        );
        int maximumBucketY = clamp(
                (int) Math.floor((mouseY + radius - cachedTop) / HOVER_BUCKET_SIZE),
                0,
                hoverBucketRows - 1
        );

        double maximumDistanceSquared = radius * radius;
        double bestDistanceSquared = maximumDistanceSquared;
        int bestIndex = -1;

        for (int bucketY = minimumBucketY; bucketY <= maximumBucketY; bucketY++) {
            for (int bucketX = minimumBucketX; bucketX <= maximumBucketX; bucketX++) {
                int bucket = bucketY * hoverBucketColumns + bucketX;
                for (int index = hoverBucketHeads[bucket];
                     index >= 0;
                     index = nextInHoverBucket[index]) {
                    lastHoverCandidateChecks++;
                    double deltaX = projectedX[index] - mouseX;
                    double deltaY = projectedY[index] - mouseY;
                    double distanceSquared = deltaX * deltaX + deltaY * deltaY;
                    if (distanceSquared < bestDistanceSquared
                            || (distanceSquared == bestDistanceSquared
                            && (bestIndex < 0 || index < bestIndex))) {
                        bestDistanceSquared = distanceSquared;
                        bestIndex = index;
                    }
                }
            }
        }
        return bestIndex;
    }

    private void drawGrid(
            GuiGraphics graphics,
            Font font,
            CoordinateTransform transform,
            int left,
            int top,
            int right,
            int bottom
    ) {
        double step = niceCeiling(transform.blocksPerPixel() * 80.0);
        if (!Double.isFinite(step) || step <= 0.0) {
            return;
        }

        double minimumX = transform.visibleMinX();
        double maximumX = transform.visibleMaxX();
        double minimumZ = transform.visibleMinZ();
        double maximumZ = transform.visibleMaxZ();

        double firstX = firstMultipleAtOrAbove(minimumX, step);
        for (int line = 0; line < 512 && firstX <= maximumX + step * 1.0e-9; line++) {
            int screenX = roundedCoordinate(transform.worldToScreenX(firstX));
            if (screenX >= left && screenX < right) {
                graphics.vLine(screenX, top, bottom - 1, GRID_COLOR);
                String label = compactNumber(firstX);
                int labelX = screenX - font.width(label) / 2;
                int labelY = bottom - font.lineHeight - 2;
                if (labelX >= left + 2 && labelX + font.width(label) < right - 2) {
                    graphics.drawString(font, label, labelX, labelY, GRID_LABEL_COLOR, false);
                }
            }
            firstX += step;
        }

        double firstZ = firstMultipleAtOrAbove(minimumZ, step);
        for (int line = 0; line < 512 && firstZ <= maximumZ + step * 1.0e-9; line++) {
            int screenY = roundedCoordinate(transform.worldToScreenZ(firstZ));
            if (screenY >= top && screenY < bottom) {
                graphics.hLine(left, right - 1, screenY, GRID_COLOR);
                String label = compactNumber(firstZ);
                int labelY = screenY - font.lineHeight / 2;
                if (labelY >= top + 2 && labelY + font.lineHeight < bottom - 2) {
                    graphics.drawString(
                            font,
                            label,
                            left + 3,
                            labelY,
                            GRID_LABEL_COLOR,
                            false
                    );
                }
            }
            firstZ += step;
        }
    }

    private void drawDistanceRings(
            GuiGraphics graphics,
            Font font,
            CoordinateTransform transform,
            int left,
            int top,
            int right,
            int bottom
    ) {
        double minimumX = transform.visibleMinX();
        double maximumX = transform.visibleMaxX();
        double minimumZ = transform.visibleMinZ();
        double maximumZ = transform.visibleMaxZ();

        double nearestX = distanceToRange(0.0, minimumX, maximumX);
        double nearestZ = distanceToRange(0.0, minimumZ, maximumZ);
        double minimumRadius = Math.hypot(nearestX, nearestZ);
        double farthestX = Math.max(Math.abs(minimumX), Math.abs(maximumX));
        double farthestZ = Math.max(Math.abs(minimumZ), Math.abs(maximumZ));
        double maximumRadius = Math.hypot(farthestX, farthestZ);
        double ringStep = niceCeiling(Math.max(
                transform.blocksPerPixel() * 72.0,
                maximumRadius / MAX_RING_COUNT
        ));
        if (!Double.isFinite(ringStep) || ringStep <= 0.0
                || !Double.isFinite(maximumRadius)) {
            return;
        }

        double ringRadius = Math.max(
                ringStep,
                firstMultipleAtOrAbove(minimumRadius, ringStep)
        );
        double centerX = transform.worldToScreenX(0.0);
        double centerY = transform.worldToScreenZ(0.0);

        for (int ring = 0;
             ring < MAX_RING_COUNT && ringRadius <= maximumRadius + ringStep * 1.0e-9;
             ring++) {
            double pixelRadius = ringRadius / transform.blocksPerPixel();
            computeVisibleArcs(
                    centerX,
                    centerY,
                    pixelRadius,
                    left,
                    top,
                    right,
                    bottom,
                    visibleRingArcs
            );
            drawCircle(
                    graphics,
                    centerX,
                    centerY,
                    pixelRadius,
                    left,
                    top,
                    right,
                    bottom,
                    visibleRingArcs
            );
            drawRingLabel(
                    graphics,
                    font,
                    compactNumber(ringRadius),
                    centerX,
                    centerY,
                    pixelRadius,
                    left,
                    top,
                    right,
                    bottom,
                    visibleRingArcs
            );
            ringRadius += ringStep;
        }
    }

    private static void drawCircle(
            GuiGraphics graphics,
            double centerX,
            double centerY,
            double radius,
            int left,
            int top,
            int right,
            int bottom,
            VisibleArcSet visibleArcs
    ) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)
                || !Double.isFinite(radius) || radius <= 0.0) {
            return;
        }

        if (visibleArcs.arcCount == 0) {
            // A tangent circle has no positive-length visible interval. Its
            // boundary angle still supplies the one drawable contact pixel.
            for (int boundary = 0; boundary < visibleArcs.boundaryCount; boundary++) {
                plotRingPoint(
                        graphics,
                        centerX,
                        centerY,
                        radius,
                        visibleArcs.boundaries[boundary],
                        left,
                        top,
                        right,
                        bottom
                );
            }
            return;
        }

        double totalSpan = 0.0;
        for (int arc = 0; arc < visibleArcs.arcCount; arc++) {
            totalSpan += visibleArcs.ends[arc] - visibleArcs.starts[arc];
        }
        double totalArcLength = radius * totalSpan;
        int minimumSamples = Math.max(
                visibleArcs.arcCount * 2,
                (int) Math.ceil(MIN_CIRCLE_SEGMENTS * totalSpan / TWO_PI)
        );
        int desiredSamples;
        if (!Double.isFinite(totalArcLength)
                || totalArcLength / TARGET_RING_SAMPLE_SPACING >= MAX_CIRCLE_SEGMENTS) {
            desiredSamples = MAX_CIRCLE_SEGMENTS;
        } else {
            desiredSamples = (int) Math.ceil(totalArcLength / TARGET_RING_SAMPLE_SPACING)
                    + visibleArcs.arcCount;
        }
        int sampleCount = clamp(
                Math.max(minimumSamples, desiredSamples),
                visibleArcs.arcCount * 2,
                MAX_CIRCLE_SEGMENTS
        );

        int extraSamples = sampleCount - visibleArcs.arcCount * 2;
        int allocatedExtras = 0;
        double cumulativeSpan = 0.0;
        for (int arc = 0; arc < visibleArcs.arcCount; arc++) {
            double start = visibleArcs.starts[arc];
            double span = visibleArcs.ends[arc] - start;
            cumulativeSpan += span;
            int cumulativeExtras = arc + 1 == visibleArcs.arcCount
                    ? extraSamples
                    : (int) Math.round(extraSamples * cumulativeSpan / totalSpan);
            int arcSamples = 2 + cumulativeExtras - allocatedExtras;
            allocatedExtras = cumulativeExtras;

            int previousX = OUTSIDE;
            int previousY = OUTSIDE;
            for (int sample = 0; sample < arcSamples; sample++) {
                double angle = start + span * sample / (arcSamples - 1.0);
                int x = roundedCoordinate(centerX + Math.cos(angle) * radius);
                int y = roundedCoordinate(centerY + Math.sin(angle) * radius);
                if (x == previousX && y == previousY) {
                    continue;
                }
                previousX = x;
                previousY = y;
                plotRingPixel(graphics, x, y, left, top, right, bottom);
            }
        }
    }

    /**
     * Splits the circle at every vertical/horizontal viewport crossing, then
     * retains only intervals whose midpoint is drawable. A circle and an
     * axis-aligned rectangle have at most eight crossings, so all storage is
     * fixed and the operation remains bounded even when the origin is far away.
     */
    static void computeVisibleArcs(
            double centerX,
            double centerY,
            double radius,
            int left,
            int top,
            int right,
            int bottom,
            VisibleArcSet result
    ) {
        result.boundaryCount = 0;
        result.arcCount = 0;
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)
                || !Double.isFinite(radius) || radius <= 0.0
                || right <= left || bottom <= top) {
            return;
        }

        double minimumX = left;
        double maximumX = right - 1.0;
        double minimumY = top;
        double maximumY = bottom - 1.0;
        int count = 0;
        result.boundaries[count++] = 0.0;
        result.boundaries[count++] = TWO_PI;
        count = addCosineBoundaries((minimumX - centerX) / radius, result.boundaries, count);
        count = addCosineBoundaries((maximumX - centerX) / radius, result.boundaries, count);
        count = addSineBoundaries((minimumY - centerY) / radius, result.boundaries, count);
        count = addSineBoundaries((maximumY - centerY) / radius, result.boundaries, count);
        Arrays.sort(result.boundaries, 0, count);

        int uniqueCount = 0;
        for (int index = 0; index < count; index++) {
            double angle = result.boundaries[index];
            if (uniqueCount == 0
                    || angle - result.boundaries[uniqueCount - 1] > ARC_ANGLE_EPSILON) {
                result.boundaries[uniqueCount++] = angle;
            }
        }
        result.boundaryCount = uniqueCount;

        for (int boundary = 0; boundary + 1 < uniqueCount; boundary++) {
            double start = result.boundaries[boundary];
            double end = result.boundaries[boundary + 1];
            if (end - start <= ARC_ANGLE_EPSILON) {
                continue;
            }
            double midpoint = start + (end - start) * 0.5;
            if (circlePointWithinViewport(
                    centerX,
                    centerY,
                    radius,
                    midpoint,
                    minimumX,
                    minimumY,
                    maximumX,
                    maximumY
            )) {
                result.starts[result.arcCount] = start;
                result.ends[result.arcCount] = end;
                result.arcCount++;
            }
        }
    }

    private static int addCosineBoundaries(double ratio, double[] boundaries, int count) {
        if (!Double.isFinite(ratio) || ratio < -1.0 || ratio > 1.0) {
            return count;
        }
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, ratio)));
        boundaries[count++] = angle;
        boundaries[count++] = TWO_PI - angle;
        return count;
    }

    private static int addSineBoundaries(double ratio, double[] boundaries, int count) {
        if (!Double.isFinite(ratio) || ratio < -1.0 || ratio > 1.0) {
            return count;
        }
        double angle = Math.asin(Math.max(-1.0, Math.min(1.0, ratio)));
        boundaries[count++] = normalizedAngle(angle);
        boundaries[count++] = normalizedAngle(Math.PI - angle);
        return count;
    }

    private static double normalizedAngle(double angle) {
        double normalized = angle % TWO_PI;
        if (normalized < 0.0) {
            normalized += TWO_PI;
        }
        return normalized == -0.0 ? 0.0 : normalized;
    }

    private static boolean circlePointWithinViewport(
            double centerX,
            double centerY,
            double radius,
            double angle,
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY
    ) {
        double x = centerX + Math.cos(angle) * radius;
        double y = centerY + Math.sin(angle) * radius;
        return x >= minimumX - 1.0e-7 && x <= maximumX + 1.0e-7
                && y >= minimumY - 1.0e-7 && y <= maximumY + 1.0e-7;
    }

    private static void plotRingPoint(
            GuiGraphics graphics,
            double centerX,
            double centerY,
            double radius,
            double angle,
            int left,
            int top,
            int right,
            int bottom
    ) {
        plotRingPixel(
                graphics,
                roundedCoordinate(centerX + Math.cos(angle) * radius),
                roundedCoordinate(centerY + Math.sin(angle) * radius),
                left,
                top,
                right,
                bottom
        );
    }

    private static void plotRingPixel(
            GuiGraphics graphics,
            int x,
            int y,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (x != OUTSIDE && y != OUTSIDE && x >= left && x < right && y >= top && y < bottom) {
            graphics.fill(x, y, x + 1, y + 1, RING_COLOR);
        }
    }

    private static void drawRingLabel(
            GuiGraphics graphics,
            Font font,
            String label,
            double centerX,
            double centerY,
            double radius,
            int left,
            int top,
            int right,
            int bottom,
            VisibleArcSet visibleArcs
    ) {
        int labelWidth = font.width(label);
        int minimumLabelX = left + 2;
        int maximumLabelX = right - labelWidth - 2;
        int minimumLabelY = top + 2;
        int maximumLabelY = bottom - font.lineHeight - 2;
        if (maximumLabelX < minimumLabelX || maximumLabelY < minimumLabelY) {
            return;
        }

        double angle = selectVisibleLabelAngle(
                centerX,
                centerY,
                radius,
                left,
                top,
                right,
                bottom,
                visibleArcs
        );
        if (!Double.isFinite(angle)) {
            return;
        }
        int pointX = roundedCoordinate(centerX + Math.cos(angle) * radius);
        int pointY = roundedCoordinate(centerY + Math.sin(angle) * radius);
        if (pointX == OUTSIDE || pointY == OUTSIDE) {
            return;
        }

        int desiredLabelX = pointX + 3;
        if (desiredLabelX > maximumLabelX) {
            desiredLabelX = pointX - labelWidth - 3;
        }
        int labelX = clamp(desiredLabelX, minimumLabelX, maximumLabelX);
        int labelY = clamp(
                pointY - font.lineHeight / 2,
                minimumLabelY,
                maximumLabelY
        );
        graphics.fill(
                labelX - 2,
                labelY - 1,
                labelX + labelWidth + 2,
                labelY + font.lineHeight,
                LABEL_BACKGROUND_COLOR
        );
        graphics.drawString(font, label, labelX, labelY, RING_LABEL_COLOR, false);
    }

    static double selectVisibleLabelAngle(
            double centerX,
            double centerY,
            double radius,
            int left,
            int top,
            int right,
            int bottom,
            VisibleArcSet visibleArcs
    ) {
        for (int arc = 0; arc < visibleArcs.arcCount; arc++) {
            if (PREFERRED_RING_LABEL_ANGLE >= visibleArcs.starts[arc] - ARC_ANGLE_EPSILON
                    && PREFERRED_RING_LABEL_ANGLE <= visibleArcs.ends[arc] + ARC_ANGLE_EPSILON) {
                return PREFERRED_RING_LABEL_ANGLE;
            }
        }

        int longestArc = -1;
        double longestSpan = -1.0;
        for (int arc = 0; arc < visibleArcs.arcCount; arc++) {
            double span = visibleArcs.ends[arc] - visibleArcs.starts[arc];
            if (span > longestSpan) {
                longestSpan = span;
                longestArc = arc;
            }
        }
        if (longestArc >= 0) {
            return visibleArcs.starts[longestArc] + longestSpan * 0.5;
        }

        double minimumX = left;
        double maximumX = right - 1.0;
        double minimumY = top;
        double maximumY = bottom - 1.0;
        for (int boundary = 0; boundary < visibleArcs.boundaryCount; boundary++) {
            double angle = visibleArcs.boundaries[boundary];
            if (circlePointWithinViewport(
                    centerX,
                    centerY,
                    radius,
                    angle,
                    minimumX,
                    minimumY,
                    maximumX,
                    maximumY
            )) {
                return angle;
            }
        }
        return Double.NaN;
    }

    private static void drawAxesAndOrigin(
            GuiGraphics graphics,
            Font font,
            CoordinateTransform transform,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int originX = roundedCoordinate(transform.worldToScreenX(0.0));
        int originY = roundedCoordinate(transform.worldToScreenZ(0.0));
        boolean verticalAxisVisible = originX >= left && originX < right;
        boolean horizontalAxisVisible = originY >= top && originY < bottom;

        if (verticalAxisVisible) {
            graphics.vLine(originX, top, bottom - 1, AXIS_COLOR);
            graphics.drawString(
                    font,
                    "Z",
                    clamp(originX + 3, left + 2, Math.max(left + 2, right - font.width("Z") - 2)),
                    top + 3,
                    AXIS_COLOR,
                    false
            );
        }
        if (horizontalAxisVisible) {
            graphics.hLine(left, right - 1, originY, AXIS_COLOR);
            graphics.drawString(
                    font,
                    "X",
                    right - font.width("X") - 3,
                    clamp(
                            originY - font.lineHeight - 2,
                            top + 2,
                            Math.max(top + 2, bottom - font.lineHeight - 2)
                    ),
                    AXIS_COLOR,
                    false
            );
        }
        if (verticalAxisVisible && horizontalAxisVisible) {
            graphics.hLine(originX - 4, originX + 4, originY, ORIGIN_COLOR);
            graphics.vLine(originX, originY - 4, originY + 4, ORIGIN_COLOR);
            graphics.fill(originX - 1, originY - 1, originX + 2, originY + 2, ORIGIN_COLOR);

            String originLabel = "0,0";
            int labelX = originX + 6;
            int labelY = originY + 4;
            if (labelX + font.width(originLabel) < right - 2
                    && labelY + font.lineHeight < bottom - 2) {
                graphics.drawString(
                        font,
                        originLabel,
                        labelX,
                        labelY,
                        ORIGIN_COLOR,
                        false
                );
            }
        }
    }

    private void drawPoints(GuiGraphics graphics, int baseSize) {
        for (int point = 0; point < renderedCount; point++) {
            int size = baseSize;
            if (renderedDensity[point] >= 8) {
                size = Math.min(8, size + 1);
            }
            if (renderedDensity[point] >= 64) {
                size = Math.min(8, size + 1);
            }
            int half = size / 2;
            int x = renderedX[point] - half;
            int y = renderedY[point] - half;
            graphics.fill(x, y, x + size, y + size, renderedColors[point]);
        }
    }

    private void drawHoverMarker(GuiGraphics graphics, int index) {
        int x = projectedX[index];
        int y = projectedY[index];
        if (x == OUTSIDE) {
            return;
        }
        graphics.hLine(x - 5, x + 5, y - 5, HOVER_COLOR);
        graphics.hLine(x - 5, x + 5, y + 5, HOVER_COLOR);
        graphics.vLine(x - 5, y - 5, y + 5, HOVER_COLOR);
        graphics.vLine(x + 5, y - 5, y + 5, HOVER_COLOR);
    }

    private static List<Component> tooltipFor(ChartPointProvider provider, int index) {
        double x = provider.xAt(index);
        double y = provider.yAt(index);
        double z = provider.zAt(index);
        List<Component> lines = new ArrayList<>(9);
        lines.add(Component.literal("Sample #" + provider.sampleNumberAt(index)));
        lines.add(Component.literal("X: " + detailedNumber(x)));
        lines.add(Component.literal("Y: " + (Double.isFinite(y) ? detailedNumber(y) : "not stored")));
        lines.add(Component.literal("Z: " + detailedNumber(z)));
        lines.add(Component.literal(
                "Distance from 0,0: " + detailedNumber(Math.hypot(x, z)) + " blocks"
        ));
        lines.add(Component.literal(
                "Dimension: " + displayValue(provider.dimensionAt(index), "unknown")
        ));
        lines.add(Component.literal(
                "Requested region: " + requestedRegionName(provider.requestedRegionAt(index))
        ));
        lines.add(Component.literal(
                "Category: " + displayValue(provider.categoryAt(index), "uncategorized")
        ));
        lines.add(Component.literal(
                "Recorded: " + formatTimestamp(provider.timestampAt(index))
        ));
        return lines;
    }

    private static String formatTimestamp(long epochMillis) {
        try {
            return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
        } catch (DateTimeException ignored) {
            return Long.toString(epochMillis);
        }
    }

    private static String displayValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static int colorForRequestedRegion(String requestedRegion) {
        return RtpRegion.fromId(requestedRegion).orElse(RtpRegion.UNKNOWN).colorArgb();
    }

    private static String requestedRegionName(String requestedRegion) {
        return RtpRegion.fromId(requestedRegion)
            .orElse(RtpRegion.UNKNOWN)
            .displayName();
    }

    private static String detailedNumber(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(Locale.ROOT, "%,.2f", value);
    }

    private static String compactNumber(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000.0) {
            return trimmedDecimal(value / 1_000_000_000.0) + "B";
        }
        if (absolute >= 1_000_000.0) {
            return trimmedDecimal(value / 1_000_000.0) + "M";
        }
        if (absolute >= 1_000.0) {
            return trimmedDecimal(value / 1_000.0) + "k";
        }
        if (absolute >= 10.0 || value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return trimmedDecimal(value);
    }

    private static String trimmedDecimal(double value) {
        String formatted = String.format(Locale.ROOT, "%.1f", value);
        if (formatted.endsWith(".0")) {
            return formatted.substring(0, formatted.length() - 2);
        }
        return formatted;
    }

    private static double niceCeiling(double rawStep) {
        if (!Double.isFinite(rawStep) || rawStep <= 0.0) {
            return Double.NaN;
        }
        double power = Math.pow(10.0, Math.floor(Math.log10(rawStep)));
        double fraction = rawStep / power;
        double niceFraction;
        if (fraction <= 1.0) {
            niceFraction = 1.0;
        } else if (fraction <= 2.0) {
            niceFraction = 2.0;
        } else if (fraction <= 5.0) {
            niceFraction = 5.0;
        } else {
            niceFraction = 10.0;
        }
        return niceFraction * power;
    }

    private static double firstMultipleAtOrAbove(double value, double step) {
        double multiple = Math.ceil(value / step) * step;
        return multiple == -0.0 ? 0.0 : multiple;
    }

    private static double distanceToRange(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        if (value > maximum) {
            return value - maximum;
        }
        return 0.0;
    }

    private static int normalizedPointSize(double pointSize) {
        if (!Double.isFinite(pointSize)) {
            return 2;
        }
        return clamp((int) Math.round(pointSize), 1, 8);
    }

    private static int roundedCoordinate(double coordinate) {
        if (!Double.isFinite(coordinate)
                || coordinate <= Integer.MIN_VALUE + 1.0
                || coordinate >= Integer.MAX_VALUE) {
            return OUTSIDE;
        }
        return (int) Math.round(coordinate);
    }

    private static int endCoordinate(int start, int extent) {
        long end = (long) start + extent;
        if (end > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (end < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) end;
    }

    private static int positiveCeilingDivision(int value, int divisor) {
        return (value - 1) / divisor + 1;
    }

    private static int checkedCellCount(int columns, int rows) {
        long count = (long) columns * rows;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Chart bounds are too large");
        }
        return (int) count;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void drawBorder(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        graphics.fill(left, top, right, top + 1, BORDER_COLOR);
        graphics.fill(left, bottom - 1, right, bottom, BORDER_COLOR);
        graphics.fill(left, top, left + 1, bottom, BORDER_COLOR);
        graphics.fill(right - 1, top, right, bottom, BORDER_COLOR);
    }

    private void ensureProjectionCapacity(int size) {
        if (projectedX.length >= size) {
            return;
        }
        int capacity = grownCapacity(projectedX.length, size);
        projectedX = new int[capacity];
        projectedY = new int[capacity];
        nextInHoverBucket = new int[capacity];
    }

    private void ensureRenderedCapacity(int size) {
        if (renderedIndices.length >= size) {
            return;
        }
        int capacity = grownCapacity(renderedIndices.length, size);
        renderedIndices = Arrays.copyOf(renderedIndices, capacity);
        renderedX = Arrays.copyOf(renderedX, capacity);
        renderedY = Arrays.copyOf(renderedY, capacity);
        renderedDensity = Arrays.copyOf(renderedDensity, capacity);
        renderedColors = Arrays.copyOf(renderedColors, capacity);
    }

    private void ensureBinCapacity(int size) {
        if (binRepresentative.length >= size) {
            return;
        }
        int capacity = grownCapacity(binRepresentative.length, size);
        binRepresentative = new int[capacity];
        binDensity = new int[capacity];
    }

    private void ensureUniquePixelCapacity(int size) {
        if (uniquePixelIndices.length >= size) {
            return;
        }
        int capacity = grownCapacity(uniquePixelIndices.length, size);
        uniquePixelIndices = new int[capacity];
        uniquePixelDensity = new int[capacity];
    }

    private void ensureHoverHashCapacity(int expectedEntries) {
        if (expectedEntries == 0) {
            return;
        }
        long requested = Math.max(2L, (long) expectedEntries * 2L);
        int capacity = 1;
        while (capacity < requested && capacity < (1 << 30)) {
            capacity <<= 1;
        }
        if (capacity < requested) {
            throw new IllegalArgumentException("Chart contains too many hoverable pixels");
        }
        if (hoverHashGeneration.length >= capacity) {
            hoverHashMask = hoverHashGeneration.length - 1;
            return;
        }
        hoverPixelKeys = new int[capacity];
        hoverPixelOrdinals = new int[capacity];
        hoverHashGeneration = new int[capacity];
        hoverHashMask = capacity - 1;
        hoverGeneration = 0;
    }

    private int nextHoverGeneration() {
        if (hoverGeneration == Integer.MAX_VALUE) {
            Arrays.fill(hoverHashGeneration, 0);
            hoverGeneration = 1;
        } else {
            hoverGeneration++;
        }
        return hoverGeneration;
    }

    private static int mixedHash(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        return value ^ (value >>> 16);
    }

    private static int grownCapacity(int current, int requested) {
        long grown = Math.max(requested, Math.max(16L, current + current / 2L));
        return (int) Math.min(Integer.MAX_VALUE, grown);
    }
}
