package com.donutsmp.rtpmapper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.donutsmp.rtpmapper.mining.MiningSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MiningViewsTest {
    @Test
    void miningSettingsAreNormalizedAndCopied() {
        ArrayList<String> blocks = new ArrayList<>(List.of("Diamond_Ore", "minecraft:diamond_ore"));
        MiningSettingsView view = new MiningSettingsView(
            true,
            List.of("PRIVATE.EXAMPLE", "*.Friends.Example"),
            blocks,
            48,
            12.5
        );
        blocks.clear();

        assertEquals(List.of("minecraft:diamond_ore"), view.blockIds());
        assertEquals(List.of("private.example", "*.friends.example"), view.allowedServers());
        assertEquals(48, view.quantity());
        assertEquals(12.5, view.timeoutMinutes());
        assertThrows(UnsupportedOperationException.class, () -> view.blockIds().clear());
    }

    @Test
    void defaultsDenyMultiplayerAndUseBoundedMiningDefaults() {
        MiningSettingsView defaults = MiningSettingsView.defaults();

        assertTrue(defaults.allowSingleplayer());
        assertTrue(defaults.allowedServers().isEmpty());
        assertEquals(MiningSettings.DEFAULT_BLOCK_IDS, defaults.blockIds());
        assertEquals(MiningSettings.DEFAULT_QUANTITY, defaults.quantity());
        assertEquals(MiningSettings.DEFAULT_TIMEOUT_MINUTES, defaults.timeoutMinutes());
    }

    @Test
    void invalidMiningSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new MiningSettingsView(true, List.of(), List.of(), 1, 10));
        assertThrows(IllegalArgumentException.class,
            () -> new MiningSettingsView(true, List.of(), List.of("diamond_ore"), 0, 10));
        assertThrows(IllegalArgumentException.class,
            () -> new MiningSettingsView(true, List.of(), List.of("diamond_ore"), 1, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new MiningSettingsView(true, List.of("*.*.example"), List.of("diamond_ore"), 1, 10));
    }

    @Test
    void miningStatusSanitizesOptionalTextAndCopiesTargets() {
        ArrayList<String> targets = new ArrayList<>(List.of("minecraft:ancient_debris"));
        MiningStatusView status = new MiningStatusView(
            true,
            false,
            true,
            null,
            null,
            null,
            targets,
            16,
            -1
        );
        targets.clear();

        assertEquals("IDLE", status.state());
        assertEquals("", status.detail());
        assertEquals("", status.serverDescription());
        assertEquals(List.of("minecraft:ancient_debris"), status.targets());
        assertThrows(UnsupportedOperationException.class, () -> status.targets().clear());
    }
}
