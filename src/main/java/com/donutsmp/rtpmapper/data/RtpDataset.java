package com.donutsmp.rtpmapper.data;

import com.donutsmp.rtpmapper.region.RtpRegion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Thread-safe mutable owner for persistent all-time and ephemeral session data. */
public final class RtpDataset {
    private final List<RtpSample> allTimeSamples = new ArrayList<>();
    private final List<RtpSample> sessionSamples = new ArrayList<>();
    private final Set<Long> sampleNumbers = new HashSet<>();

    private long revision;
    private long allTimeRevision;
    private long sessionRevision;
    private long nextSampleNumber = 1;
    private List<RtpSample> cachedAllTimeView;
    private List<RtpSample> cachedSessionView;
    private RtpDatasetSnapshot cachedSnapshot;

    public RtpDataset() {
    }

    /** Loaded samples become all-time data; the new process session starts empty. */
    public RtpDataset(Collection<RtpSample> loadedSamples) {
        replaceAllTime(loadedSamples);
    }

    public synchronized RtpSample addSample(
            double x,
            double y,
            double z,
            String dimension,
            long timestamp
    ) {
        return addSample(x, y, z, dimension, timestamp, RtpRegion.UNKNOWN, SampleCategory.DEFAULT);
    }

    public synchronized RtpSample addSample(
            double x,
            double y,
            double z,
            String dimension,
            long timestamp,
            SampleCategory category
    ) {
        return addSample(x, y, z, dimension, timestamp, RtpRegion.UNKNOWN, category);
    }

    public synchronized RtpSample addSample(
            double x,
            double y,
            double z,
            String dimension,
            long timestamp,
            RtpRegion requestedRegion
    ) {
        return addSample(x, y, z, dimension, timestamp, requestedRegion, SampleCategory.DEFAULT);
    }

    public synchronized RtpSample addSample(
            double x,
            double y,
            double z,
            String dimension,
            long timestamp,
            RtpRegion requestedRegion,
            SampleCategory category
    ) {
        if (nextSampleNumber == Long.MAX_VALUE) {
            throw new IllegalStateException("Sample number space is exhausted");
        }
        RtpSample sample = new RtpSample(
                nextSampleNumber,
                x,
                y,
                z,
                dimension,
                timestamp,
                requestedRegion,
                category
        );
        add(sample);
        return sample;
    }

    public synchronized void add(RtpSample sample) {
        Objects.requireNonNull(sample, "sample");
        if (!sampleNumbers.add(sample.sampleNumber())) {
            throw new IllegalArgumentException("Duplicate sample number: " + sample.sampleNumber());
        }
        allTimeSamples.add(sample);
        sessionSamples.add(sample);
        if (sample.sampleNumber() >= nextSampleNumber) {
            if (sample.sampleNumber() == Long.MAX_VALUE) {
                nextSampleNumber = Long.MAX_VALUE;
            } else {
                nextSampleNumber = sample.sampleNumber() + 1;
            }
        }
        revision++;
        allTimeRevision++;
        sessionRevision++;
        cachedAllTimeView = null;
        cachedSessionView = null;
        cachedSnapshot = null;
    }

    public synchronized void replaceAllTime(Collection<RtpSample> samples) {
        Objects.requireNonNull(samples, "samples");
        List<RtpSample> validated = new ArrayList<>(samples.size());
        Set<Long> numbers = new HashSet<>(Math.max(16, samples.size() * 2));
        long next = 1;
        for (RtpSample sample : samples) {
            Objects.requireNonNull(sample, "sample");
            if (!numbers.add(sample.sampleNumber())) {
                throw new IllegalArgumentException("Duplicate sample number: " + sample.sampleNumber());
            }
            validated.add(sample);
            if (sample.sampleNumber() >= next) {
                next = sample.sampleNumber() == Long.MAX_VALUE
                        ? Long.MAX_VALUE
                        : sample.sampleNumber() + 1;
            }
        }

        allTimeSamples.clear();
        allTimeSamples.addAll(validated);
        sampleNumbers.clear();
        sampleNumbers.addAll(numbers);
        sessionSamples.clear();
        nextSampleNumber = next;
        revision++;
        allTimeRevision++;
        sessionRevision++;
        cachedAllTimeView = null;
        cachedSessionView = null;
        cachedSnapshot = null;
    }

    /** Starts a fresh in-memory session without deleting persistent samples. */
    public synchronized void startNewSession() {
        if (sessionSamples.isEmpty()) {
            return;
        }
        sessionSamples.clear();
        revision++;
        sessionRevision++;
        cachedSessionView = null;
        cachedSnapshot = null;
    }

    public synchronized void clearAll() {
        if (allTimeSamples.isEmpty() && sessionSamples.isEmpty()) {
            return;
        }
        allTimeSamples.clear();
        sessionSamples.clear();
        sampleNumbers.clear();
        nextSampleNumber = 1;
        revision++;
        allTimeRevision++;
        sessionRevision++;
        cachedAllTimeView = null;
        cachedSessionView = null;
        cachedSnapshot = null;
    }

    public synchronized RtpDatasetSnapshot snapshot() {
        if (cachedSnapshot == null) {
            if (cachedAllTimeView == null) {
                cachedAllTimeView = List.copyOf(allTimeSamples);
            }
            if (cachedSessionView == null) {
                cachedSessionView = List.copyOf(sessionSamples);
            }
            cachedSnapshot = new RtpDatasetSnapshot(
                    revision,
                    allTimeRevision,
                    sessionRevision,
                    cachedAllTimeView,
                    cachedSessionView
            );
        }
        return cachedSnapshot;
    }

    public synchronized long nextSampleNumber() {
        return nextSampleNumber;
    }

    public synchronized int size() {
        return allTimeSamples.size();
    }

    public synchronized int sessionSize() {
        return sessionSamples.size();
    }

    public synchronized Optional<RtpSample> lastSample() {
        return allTimeSamples.isEmpty() ? Optional.empty() : Optional.of(allTimeSamples.getLast());
    }
}
