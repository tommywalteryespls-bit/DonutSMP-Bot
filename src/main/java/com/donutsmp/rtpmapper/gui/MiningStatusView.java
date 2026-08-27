package com.donutsmp.rtpmapper.gui;

import java.util.List;

/** Read-only status snapshot for the optional Baritone mining screen. */
public record MiningStatusView(
    boolean baritoneAvailable,
    boolean running,
    boolean serverAllowed,
    String state,
    String detail,
    String serverDescription,
    List<String> targets,
    int quantity,
    double secondsRemaining
) {
    public MiningStatusView {
        state = state == null ? "IDLE" : state;
        detail = detail == null ? "" : detail;
        serverDescription = serverDescription == null ? "" : serverDescription;
        targets = targets == null ? List.of() : List.copyOf(targets);
        if (quantity < 0) {
            throw new IllegalArgumentException("Mining quantity cannot be negative.");
        }
        if (!Double.isFinite(secondsRemaining) || secondsRemaining < -1.0) {
            throw new IllegalArgumentException("Mining time remaining must be finite or -1 when unavailable.");
        }
    }
}
