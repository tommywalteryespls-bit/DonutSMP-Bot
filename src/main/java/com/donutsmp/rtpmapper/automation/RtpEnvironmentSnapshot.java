package com.donutsmp.rtpmapper.automation;

/**
 * The Minecraft adapter's complete environment observation for one client
 * tick. The connection identity must remain value-equal for the lifetime of a
 * play connection (the network handler itself is a suitable identity).
 */
public record RtpEnvironmentSnapshot(
        boolean connected,
        boolean serverAllowed,
        Object connectionIdentity,
        PositionObservation position
) {
    public static RtpEnvironmentSnapshot ready(Object connectionIdentity, PositionObservation position) {
        return new RtpEnvironmentSnapshot(true, true, connectionIdentity, position);
    }

    public static RtpEnvironmentSnapshot disconnected() {
        return new RtpEnvironmentSnapshot(false, false, null, null);
    }

    public boolean isReadyForMapping() {
        return connected && serverAllowed && connectionIdentity != null && position != null;
    }
}
