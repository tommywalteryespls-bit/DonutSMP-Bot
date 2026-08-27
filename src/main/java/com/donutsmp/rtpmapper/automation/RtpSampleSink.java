package com.donutsmp.rtpmapper.automation;

/** Client-thread port that accepts a confirmed RTP result exactly once. */
@FunctionalInterface
public interface RtpSampleSink {
    void record(RtpSampleResult result) throws Exception;
}
