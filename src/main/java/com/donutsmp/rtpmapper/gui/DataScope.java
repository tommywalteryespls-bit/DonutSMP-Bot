package com.donutsmp.rtpmapper.gui;

public enum DataScope {
    SESSION("Session"),
    ALL_TIME("All Time");

    private final String label;

    DataScope(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public DataScope other() {
        return this == SESSION ? ALL_TIME : SESSION;
    }
}
