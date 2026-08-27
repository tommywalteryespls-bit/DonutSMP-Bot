package com.donutsmp.rtpmapper.automation;

/** The most recent request-level failure, if any. */
public enum RtpFailureReason {
    NONE,
    COMMAND_SEND_FAILED,
    TELEPORT_TIMEOUT,
    STABILIZATION_TIMEOUT,
    SAMPLE_SINK_FAILED
}
