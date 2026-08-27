package com.donutsmp.rtpmapper.util;

public enum Quadrant {
    NORTH_EAST("NE"),
    NORTH_WEST("NW"),
    SOUTH_EAST("SE"),
    SOUTH_WEST("SW");

    private final String shortLabel;

    Quadrant(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    public String shortLabel() {
        return shortLabel;
    }
}
