package com.donutsmp.rtpmapper.gui;

import com.donutsmp.rtpmapper.config.ServerMatcher;
import com.donutsmp.rtpmapper.mining.MiningSettings;
import java.util.List;

/** Immutable, validated mining settings exposed to the client UI. */
public record MiningSettingsView(
    boolean allowSingleplayer,
    List<String> allowedServers,
    List<String> blockIds,
    int quantity,
    double timeoutMinutes
) {
    public MiningSettingsView {
        allowedServers = ServerMatcher.normalizeOptionalPatterns(allowedServers);
        MiningSettings validated = new MiningSettings(
            blockIds,
            quantity,
            MiningSettings.minutesToNanos(timeoutMinutes)
        );
        blockIds = validated.blockIds();
        timeoutMinutes = validated.timeoutMinutes();
    }

    public static MiningSettingsView defaults() {
        MiningSettings defaults = MiningSettings.defaults();
        return new MiningSettingsView(
            true,
            List.of(),
            defaults.blockIds(),
            defaults.quantity(),
            defaults.timeoutMinutes()
        );
    }
}
