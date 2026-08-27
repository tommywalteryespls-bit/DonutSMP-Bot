package com.donutsmp.rtpmapper.mining;

import com.donutsmp.rtpmapper.automation.AutomationCoordinator;
import com.donutsmp.rtpmapper.automation.AutomationMode;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/** Client-thread state machine for one bounded Baritone mining run. */
public final class MiningController {
    private final MiningBackend backend;
    private final LongSupplier nanoClock;
    private final AutomationCoordinator coordinator;

    private MiningState state = MiningState.IDLE;
    private MiningStopReason lastStopReason = MiningStopReason.NONE;
    private String lastErrorMessage;
    private MiningSettings activeSettings;
    private Object activeConnectionIdentity;
    private long startedAtNanos;
    private long deadlineNanos;
    private AutomationCoordinator.Lease lease;

    public MiningController(
            MiningBackend backend,
            LongSupplier nanoClock,
            AutomationCoordinator coordinator
    ) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    public MiningStartResult start(MiningSettings settings, MiningEnvironment environment) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(environment, "environment");
        if (state == MiningState.RUNNING) {
            return MiningStartResult.ALREADY_RUNNING;
        }
        if (!environment.ready()) {
            return MiningStartResult.ENVIRONMENT_UNAVAILABLE;
        }
        if (!environment.serverAllowed()) {
            return MiningStartResult.SERVER_NOT_ALLOWED;
        }
        final boolean available;
        try {
            available = backend.available();
        } catch (RuntimeException | LinkageError exception) {
            lastErrorMessage = conciseMessage(exception);
            lastStopReason = MiningStopReason.BACKEND_ERROR;
            return MiningStartResult.START_FAILED;
        }
        if (!available) {
            return MiningStartResult.BARITONE_UNAVAILABLE;
        }

        Optional<AutomationCoordinator.Lease> acquired = coordinator.tryAcquire(
                AutomationMode.BARITONE_MINING
        );
        if (acquired.isEmpty()) {
            return MiningStartResult.AUTOMATION_BUSY;
        }

        long now = nanoClock.getAsLong();
        lease = acquired.orElseThrow();
        activeSettings = settings;
        activeConnectionIdentity = environment.connectionIdentity();
        startedAtNanos = now;
        // nanoTime is allowed to wrap. Plain signed subtraction remains valid
        // for our short (at most two-hour) deadlines, while saturation would
        // incorrectly turn a wrap into an immediate timeout.
        deadlineNanos = now + settings.timeoutNanos();
        lastStopReason = MiningStopReason.NONE;
        lastErrorMessage = null;
        state = MiningState.RUNNING;
        try {
            backend.start(settings.blockIds(), settings.quantity());
            return MiningStartResult.STARTED;
        } catch (RuntimeException | LinkageError exception) {
            finish(MiningStopReason.BACKEND_ERROR, true, exception);
            return MiningStartResult.START_FAILED;
        }
    }

    public void tick(MiningEnvironment environment) {
        Objects.requireNonNull(environment, "environment");
        if (state != MiningState.RUNNING) {
            return;
        }
        if (!environment.ready()) {
            finish(MiningStopReason.DISCONNECTED, true, null);
            return;
        }
        if (environment.connectionIdentity() != activeConnectionIdentity) {
            finish(MiningStopReason.CONNECTION_CHANGED, true, null);
            return;
        }
        if (!environment.serverAllowed()) {
            finish(MiningStopReason.SERVER_NOT_ALLOWED, true, null);
            return;
        }
        if (nanoClock.getAsLong() - deadlineNanos >= 0) {
            finish(MiningStopReason.TIMEOUT, true, null);
            return;
        }
        try {
            if (!backend.isMineProcessActive()) {
                // The reflective backend releases its process/pathing handles
                // through cancellation even after the mine process goes idle.
                finish(MiningStopReason.COMPLETED, true, null);
            }
        } catch (RuntimeException | LinkageError exception) {
            finish(MiningStopReason.BACKEND_ERROR, true, exception);
        }
    }

    public boolean stop(MiningStopReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason == MiningStopReason.NONE || reason == MiningStopReason.COMPLETED) {
            throw new IllegalArgumentException("A manual stop requires a concrete cancellation reason");
        }
        if (state != MiningState.RUNNING) {
            return false;
        }
        finish(reason, true, null);
        return true;
    }

    public boolean onDisconnected(Object disconnectedConnectionIdentity) {
        if (state != MiningState.RUNNING
                || disconnectedConnectionIdentity != activeConnectionIdentity) {
            return false;
        }
        finish(MiningStopReason.DISCONNECTED, true, null);
        return true;
    }

    public boolean emergencyStop() {
        if (state != MiningState.RUNNING) {
            try {
                backend.cancelEverything();
            } catch (RuntimeException | LinkageError ignored) {
                // Emergency stop remains idempotent when Baritone is absent.
            }
            return false;
        }
        finish(MiningStopReason.EMERGENCY_STOP, true, null, true);
        return true;
    }

    public MiningState state() {
        return state;
    }

    public boolean isRunning() {
        return state == MiningState.RUNNING;
    }

    public MiningStopReason lastStopReason() {
        return lastStopReason;
    }

    public Optional<String> lastErrorMessage() {
        return Optional.ofNullable(lastErrorMessage);
    }

    public Optional<MiningSettings> activeSettings() {
        return Optional.ofNullable(activeSettings);
    }

    public OptionalLong nanosUntilDeadline() {
        if (state != MiningState.RUNNING) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(0, deadlineNanos - nanoClock.getAsLong()));
    }

    public long elapsedNanos() {
        if (state != MiningState.RUNNING) {
            return 0;
        }
        return Math.max(0, nanoClock.getAsLong() - startedAtNanos);
    }

    private void finish(MiningStopReason reason, boolean cancel, Throwable error) {
        finish(reason, cancel, error, false);
    }

    private void finish(
            MiningStopReason reason,
            boolean cancel,
            Throwable error,
            boolean emergency
    ) {
        Throwable cancellationFailure = null;
        if (emergency) {
            try {
                backend.cancelEverything();
            } catch (RuntimeException | LinkageError failure) {
                cancellationFailure = failure;
            }
        } else if (cancel) {
            try {
                backend.cancelMine();
            } catch (RuntimeException | LinkageError failure) {
                cancellationFailure = failure;
            }
        }

        try {
            if (cancellationFailure != null) {
                if (error == null) {
                    error = cancellationFailure;
                    reason = MiningStopReason.BACKEND_ERROR;
                } else {
                    error.addSuppressed(cancellationFailure);
                }
            }
        } finally {
            coordinator.release(lease);
            lease = null;
            state = MiningState.IDLE;
            activeSettings = null;
            activeConnectionIdentity = null;
            startedAtNanos = 0;
            deadlineNanos = 0;
            lastStopReason = reason;
            lastErrorMessage = error == null ? null : conciseMessage(error);
        }
    }

    private static String conciseMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

}
