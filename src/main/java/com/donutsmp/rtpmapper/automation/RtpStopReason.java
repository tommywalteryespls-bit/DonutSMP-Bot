package com.donutsmp.rtpmapper.automation;

/** Why an active mapping session most recently returned to idle. */
public enum RtpStopReason {
    NONE,
    USER_REQUEST,
    DISCONNECTED,
    SERVER_NOT_ALLOWED,
    ENVIRONMENT_UNAVAILABLE,
    CONNECTION_CHANGED,
    CONFIGURATION_ERROR,
    CENTER_GUARD_REACHED,
    WORLD_BORDER_GUARD_REACHED
}
