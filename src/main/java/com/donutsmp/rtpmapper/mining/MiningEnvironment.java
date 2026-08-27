package com.donutsmp.rtpmapper.mining;

/** Current client/server context used to fail mining closed. */
public record MiningEnvironment(
        boolean ready,
        boolean serverAllowed,
        Object connectionIdentity,
        String serverDescription
) {
    public MiningEnvironment {
        serverDescription = serverDescription == null ? "" : serverDescription;
        if (ready && connectionIdentity == null) {
            throw new IllegalArgumentException("A ready environment requires a connection identity");
        }
    }

    public static MiningEnvironment unavailable() {
        return new MiningEnvironment(false, false, null, "Unavailable");
    }

    public static MiningEnvironment ready(
            boolean serverAllowed,
            Object connectionIdentity,
            String serverDescription
    ) {
        return new MiningEnvironment(true, serverAllowed, connectionIdentity, serverDescription);
    }
}
