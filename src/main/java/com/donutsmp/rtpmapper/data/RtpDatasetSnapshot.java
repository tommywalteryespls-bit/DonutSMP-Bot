package com.donutsmp.rtpmapper.data;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable lists plus independent revisions for low-cost renderer/statistics caches. */
public record RtpDatasetSnapshot(
        long revision,
        long allTimeRevision,
        long sessionRevision,
        List<RtpSample> allTimeSamples,
        List<RtpSample> sessionSamples
) {
    public RtpDatasetSnapshot {
        if (revision < 0 || allTimeRevision < 0 || sessionRevision < 0) {
            throw new IllegalArgumentException("Dataset revisions cannot be negative");
        }
        allTimeSamples = List.copyOf(Objects.requireNonNull(allTimeSamples, "allTimeSamples"));
        sessionSamples = List.copyOf(Objects.requireNonNull(sessionSamples, "sessionSamples"));
    }

    public List<RtpSample> samples(SampleScope scope) {
        return scope == SampleScope.SESSION ? sessionSamples : allTimeSamples;
    }

    public long revision(SampleScope scope) {
        return scope == SampleScope.SESSION ? sessionRevision : allTimeRevision;
    }

    public int totalCount() {
        return allTimeSamples.size();
    }

    public int sessionCount() {
        return sessionSamples.size();
    }

    public Optional<RtpSample> lastSample(SampleScope scope) {
        List<RtpSample> samples = samples(scope);
        return samples.isEmpty() ? Optional.empty() : Optional.of(samples.getLast());
    }
}
