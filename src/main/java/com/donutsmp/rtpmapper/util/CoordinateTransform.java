package com.donutsmp.rtpmapper.util;

/**
 * Owns every conversion between Minecraft X/Z coordinates and chart pixels.
 * Minecraft positive Z is rendered downward so north (negative Z) appears up.
 */
public final class CoordinateTransform {
    public static final double MIN_BLOCKS_PER_PIXEL = 0.01;
    public static final double MAX_BLOCKS_PER_PIXEL = 10_000_000.0;

    private double centerX;
    private double centerZ;
    private double blocksPerPixel;
    private int left;
    private int top;
    private int width = 1;
    private int height = 1;
    private long revision;

    public CoordinateTransform() {
        this(0.0, 0.0, 1_000.0);
    }

    public CoordinateTransform(double centerX, double centerZ, double blocksPerPixel) {
        this.centerX = finiteOrZero(centerX);
        this.centerZ = finiteOrZero(centerZ);
        this.blocksPerPixel = clampScale(blocksPerPixel);
    }

    public void setBounds(int left, int top, int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (this.left != left || this.top != top || this.width != safeWidth || this.height != safeHeight) {
            this.left = left;
            this.top = top;
            this.width = safeWidth;
            this.height = safeHeight;
            revision++;
        }
    }

    public double worldToScreenX(double worldX) {
        return left + width * 0.5 + (worldX - centerX) / blocksPerPixel;
    }

    public double worldToScreenZ(double worldZ) {
        return top + height * 0.5 + (worldZ - centerZ) / blocksPerPixel;
    }

    public double screenToWorldX(double screenX) {
        return centerX + (screenX - (left + width * 0.5)) * blocksPerPixel;
    }

    public double screenToWorldZ(double screenY) {
        return centerZ + (screenY - (top + height * 0.5)) * blocksPerPixel;
    }

    /** Zooms while keeping the world coordinate below the cursor fixed. */
    public void zoomAt(double screenX, double screenY, double wheelAmount) {
        if (!Double.isFinite(wheelAmount) || wheelAmount == 0.0) {
            return;
        }
        double anchorX = screenToWorldX(screenX);
        double anchorZ = screenToWorldZ(screenY);
        double factor = Math.pow(1.15, -wheelAmount);
        double newScale = clampScale(blocksPerPixel * factor);
        if (newScale == blocksPerPixel) {
            return;
        }
        blocksPerPixel = newScale;
        centerX = anchorX - (screenX - (left + width * 0.5)) * blocksPerPixel;
        centerZ = anchorZ - (screenY - (top + height * 0.5)) * blocksPerPixel;
        revision++;
    }

    /** Moves the plotted data by the supplied screen-space drag delta. */
    public void panPixels(double deltaX, double deltaY) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY) || (deltaX == 0.0 && deltaY == 0.0)) {
            return;
        }
        centerX -= deltaX * blocksPerPixel;
        centerZ -= deltaY * blocksPerPixel;
        revision++;
    }

    /**
     * Fits the supplied finite bounds around an origin-centered viewport.
     * Keeping (0, 0) at the visual center makes asymmetric RTP distributions
     * directly comparable across sessions and matches the distance rings.
     */
    public void fitToBounds(double minX, double maxX, double minZ, double maxZ, double paddingFraction) {
        minX = finiteOrZero(minX);
        maxX = finiteOrZero(maxX);
        minZ = finiteOrZero(minZ);
        maxZ = finiteOrZero(maxZ);
        if (minX > maxX) {
            double swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minZ > maxZ) {
            double swap = minZ;
            minZ = maxZ;
            maxZ = swap;
        }

        double maximumAbsoluteX = Math.max(Math.abs(minX), Math.abs(maxX));
        double maximumAbsoluteZ = Math.max(Math.abs(minZ), Math.abs(maxZ));
        centerX = 0.0;
        centerZ = 0.0;

        double padding = Double.isFinite(paddingFraction)
            ? Math.clamp(paddingFraction, 0.0, 1.0)
            : 0.08;
        double usableWidth = Math.max(1.0, width * (1.0 - padding * 2.0));
        double usableHeight = Math.max(1.0, height * (1.0 - padding * 2.0));
        double xScale = Math.max(0.5, maximumAbsoluteX) / Math.max(0.5, usableWidth * 0.5);
        double zScale = Math.max(0.5, maximumAbsoluteZ) / Math.max(0.5, usableHeight * 0.5);
        blocksPerPixel = clampScale(Math.max(xScale, zScale));
        revision++;
    }

    public boolean contains(double screenX, double screenY) {
        return screenX >= left && screenX < left + width && screenY >= top && screenY < top + height;
    }

    public double visibleMinX() {
        return screenToWorldX(left);
    }

    public double visibleMaxX() {
        return screenToWorldX(left + width);
    }

    public double visibleMinZ() {
        return screenToWorldZ(top);
    }

    public double visibleMaxZ() {
        return screenToWorldZ(top + height);
    }

    public double centerX() {
        return centerX;
    }

    public double centerZ() {
        return centerZ;
    }

    public double blocksPerPixel() {
        return blocksPerPixel;
    }

    public int left() {
        return left;
    }

    public int top() {
        return top;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long revision() {
        return revision;
    }

    private static double clampScale(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 1_000.0;
        }
        return Math.clamp(value, MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
