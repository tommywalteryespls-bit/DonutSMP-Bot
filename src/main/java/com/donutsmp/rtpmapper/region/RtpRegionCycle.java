package com.donutsmp.rtpmapper.region;

import java.util.List;
import java.util.Optional;

/** Client-thread round-robin scheduler for an immutable region selection. */
public final class RtpRegionCycle {
    private RtpRegion lastIssued;

    public RtpRegionCycle() {
    }

    /** A non-selectable seed is treated like no prior region. */
    public RtpRegionCycle(RtpRegion lastIssued) {
        this.lastIssued = lastIssued != null && lastIssued.selectable() ? lastIssued : null;
    }

    /** Returns and advances to the next selected region. */
    public RtpRegion next(List<RtpRegion> selectedRegions) {
        RtpRegion next = peek(selectedRegions);
        lastIssued = next;
        return next;
    }

    /** Returns the next selected region without advancing the cycle. */
    public RtpRegion peek(List<RtpRegion> selectedRegions) {
        List<RtpRegion> normalized = RtpRegion.normalizeSelection(selectedRegions);
        if (lastIssued == null) {
            return normalized.getFirst();
        }
        int previousIndex = normalized.indexOf(lastIssued);
        if (previousIndex < 0) {
            return normalized.getFirst();
        }
        return normalized.get((previousIndex + 1) % normalized.size());
    }

    public Optional<RtpRegion> lastIssued() {
        return Optional.ofNullable(lastIssued);
    }
}
