package com.donutsmp.rtpmapper.data;

import java.util.Locale;

/** Extensible display category; no geographic meaning is inferred by the mapper. */
public enum SampleCategory {
    DEFAULT,
    REGION_1,
    REGION_2,
    REGION_3;

    public static SampleCategory parse(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }
}
