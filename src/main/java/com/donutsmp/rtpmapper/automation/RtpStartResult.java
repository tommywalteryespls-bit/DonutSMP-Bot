package com.donutsmp.rtpmapper.automation;

/** Result of a user-initiated start request. */
public enum RtpStartResult {
    STARTED,
    ALREADY_RUNNING,
    NOT_CONNECTED,
    SERVER_NOT_ALLOWED,
    POSITION_UNAVAILABLE
}
