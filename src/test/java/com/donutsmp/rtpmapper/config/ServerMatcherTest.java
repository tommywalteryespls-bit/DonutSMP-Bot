package com.donutsmp.rtpmapper.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMatcherTest {
    private static final List<String> DONUT = List.of("donutsmp.net", "*.donutsmp.net");

    @Test
    void exactAndWildcardHostsMatchWithPortsAndCase() {
        assertTrue(ServerMatcher.matches("DonutSMP.NET:25565", DONUT));
        assertTrue(ServerMatcher.matches("play.donutsmp.net", DONUT));
        assertTrue(ServerMatcher.matches("one.two.DONUTSMP.NET.:25565", DONUT));
    }

    @Test
    void wildcardIsLabelBoundAndFailsClosed() {
        assertFalse(ServerMatcher.matches("evildonutsmp.net", DONUT));
        assertFalse(ServerMatcher.matches("donutsmp.net.evil.example", DONUT));
        assertFalse(ServerMatcher.matches("singleplayer", DONUT));
        assertFalse(ServerMatcher.matches(null, DONUT));
        assertFalse(ServerMatcher.matches("play.donutsmp.net", List.of("foo.*.donutsmp.net")));
    }

    @Test
    void aWildcardDoesNotImplicitlyIncludeTheApex() {
        assertFalse(ServerMatcher.matchesPattern("donutsmp.net", "*.donutsmp.net"));
        assertTrue(ServerMatcher.matchesPattern("play.donutsmp.net", "*.donutsmp.net"));
    }
}
