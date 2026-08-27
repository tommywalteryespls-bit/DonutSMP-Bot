package com.donutsmp.rtpmapper.automation;

import java.util.Objects;
import java.util.Optional;

/**
 * Grants exclusive, generation-stamped automation leases. A stale callback
 * cannot release a newer session even when both sessions use the same mode.
 */
public final class AutomationCoordinator {
    private AutomationMode mode = AutomationMode.IDLE;
    private long generation;

    public synchronized Optional<Lease> tryAcquire(AutomationMode requestedMode) {
        Objects.requireNonNull(requestedMode, "requestedMode");
        if (requestedMode == AutomationMode.IDLE) {
            throw new IllegalArgumentException("IDLE cannot own an automation lease");
        }
        if (mode != AutomationMode.IDLE) {
            return Optional.empty();
        }
        generation++;
        mode = requestedMode;
        return Optional.of(new Lease(requestedMode, generation));
    }

    public synchronized boolean release(Lease lease) {
        if (lease == null || lease.mode() != mode || lease.generation() != generation) {
            return false;
        }
        mode = AutomationMode.IDLE;
        return true;
    }

    public synchronized AutomationMode mode() {
        return mode;
    }

    public record Lease(AutomationMode mode, long generation) {
        public Lease {
            Objects.requireNonNull(mode, "mode");
            if (mode == AutomationMode.IDLE || generation <= 0) {
                throw new IllegalArgumentException("Invalid automation lease");
            }
        }
    }
}
