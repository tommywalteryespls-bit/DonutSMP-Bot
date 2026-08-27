package com.donutsmp.rtpmapper.gui;

/**
 * Read-only, allocation-free access to the samples displayed by a
 * {@link ChartRenderer}.
 *
 * <p>The provider must present a stable snapshot for the duration of a render
 * call. {@link #revision()} must change whenever the size, order, or any value
 * exposed by this interface changes. That contract lets the renderer retain
 * projected coordinates without depending on the application's data model.</p>
 */
public interface ChartPointProvider {
    /** Returns the number of addressable samples. */
    int size();

    /**
     * Returns a monotonically changing revision for this snapshot.
     *
     * <p>The value does not need to be contiguous; equality is the only
     * operation performed by the renderer.</p>
     */
    long revision();

    /** Returns the user-facing sample number at {@code index}. */
    long sampleNumberAt(int index);

    /** Returns the sample's world X coordinate. */
    double xAt(int index);

    /** Returns the sample's world Y coordinate. */
    double yAt(int index);

    /** Returns the sample's world Z coordinate. */
    double zAt(int index);

    /** Returns the sample timestamp as milliseconds since the Unix epoch. */
    long timestampAt(int index);

    /** Returns a displayable dimension identifier, or {@code null}. */
    String dimensionAt(int index);

    /** Returns a displayable category name, or {@code null}. */
    String categoryAt(int index);

    /** Returns the stable requested RTP-region id, or {@code null} for legacy data. */
    default String requestedRegionAt(int index) {
        return null;
    }
}
