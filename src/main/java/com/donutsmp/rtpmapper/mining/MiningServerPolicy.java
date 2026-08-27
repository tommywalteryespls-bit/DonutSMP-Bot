package com.donutsmp.rtpmapper.mining;

import com.donutsmp.rtpmapper.config.ServerMatcher;
import java.util.Collection;
import java.util.List;

/** Non-configurable hostname denial plus explicit private-server allowlisting. */
public final class MiningServerPolicy {
    public static final String HARD_BLOCKED_SERVER = "donutsmp.net";
    public static final List<String> HARD_BLOCKED_SERVER_PATTERNS = List.of(
            HARD_BLOCKED_SERVER,
            "*." + HARD_BLOCKED_SERVER
    );

    private MiningServerPolicy() {
    }

    public static boolean isHardBlockedServer(String address) {
        return ServerMatcher.matches(address, HARD_BLOCKED_SERVER_PATTERNS);
    }

    public static boolean isRemoteServerAllowed(
            String address,
            Collection<String> miningAllowedServers
    ) {
        return !isHardBlockedServer(address) && ServerMatcher.matches(address, miningAllowedServers);
    }
}
