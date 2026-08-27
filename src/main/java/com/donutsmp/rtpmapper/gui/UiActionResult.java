package com.donutsmp.rtpmapper.gui;

public record UiActionResult(boolean success, String message) {
    public UiActionResult {
        message = message == null ? "" : message;
    }

    public static UiActionResult ok(String message) {
        return new UiActionResult(true, message);
    }

    public static UiActionResult error(String message) {
        return new UiActionResult(false, message);
    }
}
