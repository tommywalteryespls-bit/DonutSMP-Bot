package com.donutsmp.rtpmapper.automation;

import com.donutsmp.rtpmapper.region.RtpRegion;

/** Client-thread port that sends one explicit, region-scoped {@code /rtp} command. */
@FunctionalInterface
public interface RtpCommandSender {
    void sendRtpCommand(long requestNumber, RtpRegion requestedRegion) throws Exception;
}
