package com.donutsmp.rtpmapper.automation;

/** Separates monotonic deadlines from the wall-clock sample timestamp. */
public interface RtpClock {
    long nanoTime();

    long currentTimeMillis();

    static RtpClock system() {
        return new RtpClock() {
            @Override
            public long nanoTime() {
                return System.nanoTime();
            }

            @Override
            public long currentTimeMillis() {
                return System.currentTimeMillis();
            }
        };
    }
}
