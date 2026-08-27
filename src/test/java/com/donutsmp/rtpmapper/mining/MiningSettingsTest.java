package com.donutsmp.rtpmapper.mining;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningSettingsTest {
    @Test
    void defaultsAreBoundedAndTargetBothDiamondOreVariants() {
        MiningSettings settings = MiningSettings.defaults();

        assertEquals(List.of(
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore"
        ), settings.blockIds());
        assertEquals(64, settings.quantity());
        assertEquals(10.0, settings.timeoutMinutes());
    }

    @Test
    void normalizesNamespacesCaseWhitespaceAndDuplicates() {
        ArrayList<String> supplied = new ArrayList<>(List.of(
                " Diamond_Ore ",
                "MINECRAFT:DIAMOND_ORE",
                "mod.example:Deep/Ore",
                "   "
        ));

        MiningSettings settings = new MiningSettings(
                supplied,
                MiningSettings.MIN_QUANTITY,
                MiningSettings.minutesToNanos(1.25)
        );
        supplied.clear();

        assertEquals(List.of(
                "minecraft:diamond_ore",
                "mod.example:deep/ore"
        ), settings.blockIds());
        assertEquals(1.25, settings.timeoutMinutes());
        assertThrows(UnsupportedOperationException.class,
                () -> settings.blockIds().add("minecraft:stone"));
    }

    @Test
    void blockTargetsMustBePresentValidAndLimited() {
        assertThrows(NullPointerException.class,
                () -> MiningSettings.normalizeBlockIds(null));
        assertThrows(NullPointerException.class,
                () -> MiningSettings.normalizeBlockIds(Arrays.asList("stone", null)));
        assertThrows(IllegalArgumentException.class,
                () -> MiningSettings.normalizeBlockIds(List.of("", "   ")));
        assertThrows(IllegalArgumentException.class,
                () -> MiningSettings.normalizeBlockIds(List.of("minecraft:bad target")));
        assertThrows(IllegalArgumentException.class,
                () -> MiningSettings.normalizeBlockIds(List.of("minecraft:")));
        assertThrows(IllegalArgumentException.class,
                () -> MiningSettings.normalizeBlockIds(List.of("minecraft:" + "a".repeat(119))));

        List<String> tooMany = IntStream.rangeClosed(0, MiningSettings.MAX_TARGETS)
                .mapToObj(index -> "example:ore_" + index)
                .toList();
        assertThrows(IllegalArgumentException.class,
                () -> MiningSettings.normalizeBlockIds(tooMany));
    }

    @Test
    void quantityAndTimeoutBoundsAreInclusive() {
        long minimumTimeout = MiningSettings.minutesToNanos(MiningSettings.MIN_TIMEOUT_MINUTES);
        long maximumTimeout = MiningSettings.minutesToNanos(MiningSettings.MAX_TIMEOUT_MINUTES);

        MiningSettings minimum = new MiningSettings(
                List.of("stone"),
                MiningSettings.MIN_QUANTITY,
                minimumTimeout
        );
        MiningSettings maximum = new MiningSettings(
                List.of("stone"),
                MiningSettings.MAX_QUANTITY,
                maximumTimeout
        );

        assertEquals(Duration.ofMinutes(1).toNanos(), minimum.timeoutNanos());
        assertEquals(Duration.ofMinutes(120).toNanos(), maximum.timeoutNanos());
        assertThrows(IllegalArgumentException.class,
                () -> new MiningSettings(List.of("stone"), 0, minimumTimeout));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningSettings(
                        List.of("stone"),
                        MiningSettings.MAX_QUANTITY + 1,
                        minimumTimeout
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningSettings(List.of("stone"), 1, minimumTimeout - 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningSettings(List.of("stone"), 1, maximumTimeout + 1));
    }

    @Test
    void timeoutConversionRejectsNonFiniteAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> MiningSettings.minutesToNanos(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> MiningSettings.minutesToNanos(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> MiningSettings.minutesToNanos(MiningSettings.MIN_TIMEOUT_MINUTES - 0.001));
        assertThrows(IllegalArgumentException.class,
                () -> MiningSettings.minutesToNanos(MiningSettings.MAX_TIMEOUT_MINUTES + 0.001));

        assertTrue(MiningSettings.minutesToNanos(1.5) > Duration.ofMinutes(1).toNanos());
    }
}
