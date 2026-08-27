package com.donutsmp.rtpmapper.region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Stable identifiers and command arguments for DonutSMP's public RTP regions.
 * {@link #UNKNOWN} exists only to preserve provenance for legacy data.
 */
public enum RtpRegion {
    NA_EAST("na_east", "North America East", "NA East", "east", 0xFF4FA9DD, true),
    NA_WEST("na_west", "North America West", "NA West", "west", 0xFF2F6BB0, true),
    EU_CENTRAL("eu_central", "Europe Central", "EU Central", "eu central", 0xFF9CCC65, true),
    EU_WEST("eu_west", "Europe West", "EU West", "eu west", 0xFF00A65A, true),
    ASIA("asia", "Asia", "Asia", "asia", 0xFFF6C445, true),
    OCEANIA("oceania", "Oceania", "Oceania", "oceania", 0xFFFF8A00, true),
    UNKNOWN("unknown", "Unknown", "Unknown", null, 0xFF8D99AA, false);

    private static final List<RtpRegion> SELECTABLE_VALUES = List.of(
            NA_EAST,
            NA_WEST,
            EU_CENTRAL,
            EU_WEST,
            ASIA,
            OCEANIA
    );
    private static final List<RtpRegion> DISPLAY_VALUES = List.of(values());

    private final String id;
    private final String displayName;
    private final String shortName;
    private final String commandArgument;
    private final int colorArgb;
    private final boolean selectable;

    RtpRegion(
            String id,
            String displayName,
            String shortName,
            String commandArgument,
            int colorArgb,
            boolean selectable
    ) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.commandArgument = commandArgument;
        this.colorArgb = colorArgb;
        this.selectable = selectable;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String shortName() {
        return shortName;
    }

    /** Returns the fixed argument appended to {@code /rtp}. */
    public String commandArgument() {
        if (!selectable) {
            throw new IllegalStateException("UNKNOWN is not a selectable RTP command region");
        }
        return commandArgument;
    }

    public int colorArgb() {
        return colorArgb;
    }

    public boolean selectable() {
        return selectable;
    }

    /** The six public regions in the deterministic cycling/display order. */
    public static List<RtpRegion> selectableValues() {
        return SELECTABLE_VALUES;
    }

    /** The six public regions followed by the legacy-only unknown bucket. */
    public static List<RtpRegion> displayValues() {
        return DISPLAY_VALUES;
    }

    /**
     * Parses canonical persisted IDs and tolerant human-readable aliases.
     * The result may be {@link #UNKNOWN}; callers handling selections must
     * additionally check {@link #selectable()}.
     */
    public static Optional<RtpRegion> fromId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeToken(value);
        for (RtpRegion region : values()) {
            if (normalized.equals(normalizeToken(region.id))
                    || normalized.equals(normalizeToken(region.name()))
                    || normalized.equals(normalizeToken(region.displayName))
                    || normalized.equals(normalizeToken(region.shortName))
                    || region.commandArgument != null
                    && normalized.equals(normalizeToken(region.commandArgument))) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    public static RtpRegion parseOrUnknown(String value) {
        return fromId(value).orElse(UNKNOWN);
    }

    /**
     * Returns an immutable, duplicate-free selection in canonical region
     * order. Nulls, {@link #UNKNOWN}, and an empty result are rejected.
     */
    public static List<RtpRegion> normalizeSelection(Collection<RtpRegion> regions) {
        Objects.requireNonNull(regions, "regions");
        EnumSet<RtpRegion> selected = EnumSet.noneOf(RtpRegion.class);
        for (RtpRegion region : regions) {
            Objects.requireNonNull(region, "selected region");
            if (!region.selectable) {
                throw new IllegalArgumentException(region.shortName + " is not a selectable RTP region");
            }
            selected.add(region);
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("At least one RTP region must be selected");
        }
        List<RtpRegion> normalized = new ArrayList<>(selected.size());
        for (RtpRegion region : SELECTABLE_VALUES) {
            if (selected.contains(region)) {
                normalized.add(region);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeToken(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
