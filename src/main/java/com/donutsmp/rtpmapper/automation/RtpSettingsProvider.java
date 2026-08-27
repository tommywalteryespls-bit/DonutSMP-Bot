package com.donutsmp.rtpmapper.automation;

/** Supplies a validated immutable settings snapshot for each command attempt. */
@FunctionalInterface
public interface RtpSettingsProvider {
    RtpAttemptSettings snapshot();
}
