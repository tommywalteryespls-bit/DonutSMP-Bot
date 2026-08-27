package com.donutsmp.rtpmapper.mining;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningServerPolicyTest {
    @Test
    void recognizesHardBlockedServerApexAndEverySubdomain() {
        assertTrue(MiningServerPolicy.isHardBlockedServer("donutsmp.net"));
        assertTrue(MiningServerPolicy.isHardBlockedServer("DONUTSMP.NET.:25565"));
        assertTrue(MiningServerPolicy.isHardBlockedServer("donutsmp.net\u3002"));
        assertTrue(MiningServerPolicy.isHardBlockedServer("play.donutsmp.net"));
        assertTrue(MiningServerPolicy.isHardBlockedServer("na.east.donutsmp.net:25565"));
        assertTrue(MiningServerPolicy.isHardBlockedServer("https://play.donutsmp.net:25565"));

        assertFalse(MiningServerPolicy.isHardBlockedServer("evildonutsmp.net"));
        assertFalse(MiningServerPolicy.isHardBlockedServer("donutsmp.net.example.org"));
        assertFalse(MiningServerPolicy.isHardBlockedServer(null));
        assertFalse(MiningServerPolicy.isHardBlockedServer(""));
    }

    @Test
    void immutableHardBlockOverridesTheUserAllowlist() {
        List<String> attemptedOverrides = List.of(
                "donutsmp.net",
                "*.donutsmp.net"
        );

        assertFalse(MiningServerPolicy.isRemoteServerAllowed(
                "donutsmp.net:25565",
                attemptedOverrides
        ));
        assertFalse(MiningServerPolicy.isRemoteServerAllowed(
                "private.donutsmp.net",
                attemptedOverrides
        ));
    }

    @Test
    void remoteServersRequireAnExplicitExactOrWildcardMatch() {
        List<String> allowlist = List.of("private.example", "*.friends.example");

        assertTrue(MiningServerPolicy.isRemoteServerAllowed(
                "private.example:25565",
                allowlist
        ));
        assertTrue(MiningServerPolicy.isRemoteServerAllowed(
                "mine.friends.example",
                allowlist
        ));
        assertFalse(MiningServerPolicy.isRemoteServerAllowed(
                "friends.example",
                allowlist
        ));
        assertFalse(MiningServerPolicy.isRemoteServerAllowed(
                "public.example",
                allowlist
        ));
        assertFalse(MiningServerPolicy.isRemoteServerAllowed("private.example", List.of()));
        assertFalse(MiningServerPolicy.isRemoteServerAllowed("private.example", null));
    }

    @Test
    void malformedAddressesAndPatternsFailClosed() {
        assertFalse(MiningServerPolicy.isRemoteServerAllowed(
                "private.example:not-a-port",
                List.of("private.example")
        ));
        assertFalse(MiningServerPolicy.isRemoteServerAllowed(
                "private.example",
                List.of("*", "foo.*.example")
        ));
    }
}
