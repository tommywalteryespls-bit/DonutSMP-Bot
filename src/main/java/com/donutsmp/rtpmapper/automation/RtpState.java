package com.donutsmp.rtpmapper.automation;

/**
 * Tick-visible states in the automatic RTP lifecycle.
 *
 * <p>{@link #RECORDING} is deliberately transient. The controller enters it
 * before invoking the sample sink and leaves it before the tick returns. This
 * closes the request before any potentially failing integration callback can
 * cause it to be recorded a second time.</p>
 */
public enum RtpState {
    IDLE,
    WAITING_TO_SEND,
    WAITING_FOR_TELEPORT,
    WAITING_FOR_STABILIZATION,
    RECORDING,
    COOLDOWN
}
