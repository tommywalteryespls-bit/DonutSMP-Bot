package com.donutsmp.rtpmapper.automation;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Deterministic, single-threaded RTP state machine.
 *
 * <p>All methods must be called from the same thread. In production that is
 * the Minecraft client thread. A call to {@link #tick(RtpEnvironmentSnapshot)}
 * executes exactly one state's handler; transitions never fall through to a
 * second action in the same tick.</p>
 */
public final class RtpController {
    private final RtpClock clock;
    private final RtpSettingsProvider settingsProvider;
    private final RtpCommandSender commandSender;
    private final RtpSampleSink sampleSink;
    private final TeleportDetector teleportDetector;

    private RtpState state = RtpState.IDLE;
    private Object activeConnectionIdentity;
    private PendingRequest pending;
    private long nextActionAtNanos;
    private long requestSequence;
    private long commandAttempts;
    private long confirmedTeleports;
    private long deliveredSamples;
    private long failedRtpAttempts;
    private long sampleSinkFailures;
    private RtpFailureReason lastFailureReason = RtpFailureReason.NONE;
    private RtpStopReason lastStopReason = RtpStopReason.NONE;
    private String lastErrorMessage;
    private RtpSampleResult lastSampleResult;

    public RtpController(
            RtpClock clock,
            RtpSettingsProvider settingsProvider,
            RtpCommandSender commandSender,
            RtpSampleSink sampleSink
    ) {
        this(clock, settingsProvider, commandSender, sampleSink, new TeleportDetector());
    }

    public RtpController(
            RtpClock clock,
            RtpSettingsProvider settingsProvider,
            RtpCommandSender commandSender,
            RtpSampleSink sampleSink,
            TeleportDetector teleportDetector
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settingsProvider = Objects.requireNonNull(settingsProvider, "settingsProvider");
        this.commandSender = Objects.requireNonNull(commandSender, "commandSender");
        this.sampleSink = Objects.requireNonNull(sampleSink, "sampleSink");
        this.teleportDetector = Objects.requireNonNull(teleportDetector, "teleportDetector");
    }

    /** Starts a fresh automatic session without sending until the next tick. */
    public RtpStartResult start(RtpEnvironmentSnapshot environment) {
        Objects.requireNonNull(environment, "environment");
        if (state != RtpState.IDLE) {
            return RtpStartResult.ALREADY_RUNNING;
        }
        if (!environment.connected()) {
            return RtpStartResult.NOT_CONNECTED;
        }
        if (!environment.serverAllowed()) {
            return RtpStartResult.SERVER_NOT_ALLOWED;
        }
        if (environment.connectionIdentity() == null || environment.position() == null) {
            return RtpStartResult.POSITION_UNAVAILABLE;
        }

        activeConnectionIdentity = environment.connectionIdentity();
        pending = null;
        nextActionAtNanos = clock.nanoTime();
        lastStopReason = RtpStopReason.NONE;
        lastFailureReason = RtpFailureReason.NONE;
        lastErrorMessage = null;
        state = RtpState.WAITING_TO_SEND;
        return RtpStartResult.STARTED;
    }

    public void stop() {
        stop(RtpStopReason.USER_REQUEST);
    }

    /** Stops with an explicit externally determined reason. */
    public void stop(RtpStopReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason == RtpStopReason.NONE) {
            throw new IllegalArgumentException("A stopped controller requires a concrete reason");
        }
        stopInternal(reason, null);
    }

    /** An event-adapter convenience; tick guards provide the same protection. */
    public void onDisconnected() {
        if (state != RtpState.IDLE) {
            stopInternal(RtpStopReason.DISCONNECTED, null);
        }
    }

    public void tick(RtpEnvironmentSnapshot environment) {
        Objects.requireNonNull(environment, "environment");
        if (state == RtpState.IDLE) {
            return;
        }

        RtpStopReason invalidReason = invalidEnvironmentReason(environment);
        if (invalidReason != null) {
            stopInternal(invalidReason, null);
            return;
        }

        long now = clock.nanoTime();
        switch (state) {
            case WAITING_TO_SEND -> tickWaitingToSend(environment.position(), now);
            case WAITING_FOR_TELEPORT -> tickWaitingForTeleport(environment.position(), now);
            case WAITING_FOR_STABILIZATION -> tickWaitingForStabilization(environment.position(), now);
            case COOLDOWN -> tickCooldown(now);
            case RECORDING -> throw new IllegalStateException("RECORDING must not escape recordPending()");
            case IDLE -> {
                // Handled above.
            }
        }
    }

    private void tickWaitingToSend(PositionObservation position, long now) {
        if (!deadlineReached(now, nextActionAtNanos)) {
            return;
        }

        final RtpAttemptSettings settings;
        try {
            settings = Objects.requireNonNull(settingsProvider.snapshot(), "settingsProvider returned null");
        } catch (RuntimeException exception) {
            stopInternal(RtpStopReason.CONFIGURATION_ERROR, exception);
            return;
        }

        long requestNumber = ++requestSequence;
        RtpRequest request = new RtpRequest(
                requestNumber,
                position,
                settings,
                now,
                now + settings.teleportTimeoutNanos()
        );

        // Close the send transition before invoking integration code. Even if
        // the callback throws, this request can never be sent again.
        pending = new PendingRequest(request);
        state = RtpState.WAITING_FOR_TELEPORT;
        commandAttempts++;
        try {
            commandSender.sendRtpCommand(requestNumber, settings.requestedRegion());
        } catch (Exception exception) {
            if (state == RtpState.WAITING_FOR_TELEPORT
                    && pending != null
                    && pending.request.requestNumber() == requestNumber) {
                failPending(RtpFailureReason.COMMAND_SEND_FAILED, clock.nanoTime(), exception);
            }
            return;
        }
        if (state == RtpState.WAITING_FOR_TELEPORT
                && pending != null
                && pending.request.requestNumber() == requestNumber) {
            long sentAt = clock.nanoTime();
            pending.request = new RtpRequest(
                requestNumber,
                position,
                settings,
                sentAt,
                sentAt + settings.teleportTimeoutNanos()
            );
        }
    }

    private void tickWaitingForTeleport(PositionObservation position, long now) {
        PendingRequest current = requirePending();
        RtpRequest request = current.request;

        // A position arriving exactly at the timeout boundary wins over the
        // timeout, since the client did observe a qualifying teleport.
        if (teleportDetector.isTeleport(
                request.baseline(),
                position,
                request.settings().teleportThresholdBlocks()
        )) {
            current.beginStabilization(position, now);
            state = RtpState.WAITING_FOR_STABILIZATION;
            return;
        }

        if (deadlineReached(now, request.teleportDeadlineNanos())) {
            failPending(RtpFailureReason.TELEPORT_TIMEOUT, now, null);
        }
    }

    private void tickWaitingForStabilization(PositionObservation position, long now) {
        PendingRequest current = requirePending();
        RtpRequest request = current.request;
        RtpAttemptSettings settings = request.settings();

        // Revalidate against the command baseline. A transient large movement
        // that settles back at the old location is not a valid sample.
        if (!teleportDetector.isTeleport(
                request.baseline(),
                position,
                settings.teleportThresholdBlocks()
        )) {
            current.clearStabilization();
            state = RtpState.WAITING_FOR_TELEPORT;
            if (deadlineReached(now, request.teleportDeadlineNanos())) {
                failPending(RtpFailureReason.TELEPORT_TIMEOUT, now, null);
            }
            return;
        }

        current.observeStabilization(position, teleportDetector, settings.stabilityToleranceBlocks());
        if (current.stabilizationTicks >= settings.minimumStabilizationTicks()
                && current.stableTicks >= settings.requiredStableTicks()) {
            recordPending(position);
            return;
        }

        if (deadlineReached(now, current.stabilizationStartedAtNanos + settings.maximumStabilizationNanos())) {
            failPending(RtpFailureReason.STABILIZATION_TIMEOUT, now, null);
        }
    }

    private void recordPending(PositionObservation position) {
        PendingRequest current = requirePending();
        if (current.sampleAttempted) {
            throw new IllegalStateException("A pending RTP request may only be recorded once");
        }

        current.sampleAttempted = true;
        state = RtpState.RECORDING;
        confirmedTeleports++;

        RtpSampleResult result = new RtpSampleResult(
                current.request.requestNumber(),
                position.x(),
                position.y(),
                position.z(),
                position.dimension(),
                clock.currentTimeMillis(),
                teleportDetector.horizontalDistance(current.request.baseline(), position),
                current.request.settings().storeYCoordinate(),
                current.request.settings().requestedRegion()
        );
        lastSampleResult = result;

        Exception sinkFailure = null;
        try {
            sampleSink.record(result);
            deliveredSamples++;
        } catch (Exception exception) {
            sampleSinkFailures++;
            lastFailureReason = RtpFailureReason.SAMPLE_SINK_FAILED;
            lastErrorMessage = errorMessage(exception);
            sinkFailure = exception;
        } finally {
            long cooldown = current.request.settings().cooldownNanos();
            pending = null;
            nextActionAtNanos = clock.nanoTime() + cooldown;
            state = RtpState.COOLDOWN;
        }

        // The failure is intentionally not rethrown and the request is never
        // retried: the sink might have accepted the sample before throwing.
        if (sinkFailure == null) {
            lastFailureReason = RtpFailureReason.NONE;
            lastErrorMessage = null;
        }
    }

    private void failPending(RtpFailureReason reason, long now, Exception exception) {
        PendingRequest current = requirePending();
        failedRtpAttempts++;
        lastFailureReason = Objects.requireNonNull(reason, "reason");
        lastErrorMessage = exception == null ? null : errorMessage(exception);
        long cooldown = current.request.settings().cooldownNanos();
        pending = null;
        nextActionAtNanos = now + cooldown;
        state = RtpState.COOLDOWN;
    }

    private void tickCooldown(long now) {
        if (deadlineReached(now, nextActionAtNanos)) {
            state = RtpState.WAITING_TO_SEND;
        }
    }

    private RtpStopReason invalidEnvironmentReason(RtpEnvironmentSnapshot environment) {
        if (!environment.connected()) {
            return RtpStopReason.DISCONNECTED;
        }
        if (!environment.serverAllowed()) {
            return RtpStopReason.SERVER_NOT_ALLOWED;
        }
        if (environment.connectionIdentity() == null || environment.position() == null) {
            return RtpStopReason.ENVIRONMENT_UNAVAILABLE;
        }
        if (!Objects.equals(activeConnectionIdentity, environment.connectionIdentity())) {
            return RtpStopReason.CONNECTION_CHANGED;
        }
        return null;
    }

    private PendingRequest requirePending() {
        if (pending == null) {
            throw new IllegalStateException("State " + state + " requires one pending RTP request");
        }
        return pending;
    }

    private void stopInternal(RtpStopReason reason, Exception exception) {
        state = RtpState.IDLE;
        activeConnectionIdentity = null;
        pending = null;
        nextActionAtNanos = 0;
        lastStopReason = Objects.requireNonNull(reason, "reason");
        lastErrorMessage = exception == null ? null : errorMessage(exception);
    }

    private static boolean deadlineReached(long now, long deadline) {
        // Correct across System.nanoTime() wraparound for practical durations.
        return now - deadline >= 0;
    }

    private static String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public RtpState state() {
        return state;
    }

    public boolean isRunning() {
        return state != RtpState.IDLE;
    }

    public Optional<RtpRequest> pendingRequest() {
        return pending == null ? Optional.empty() : Optional.of(pending.request);
    }

    /** Remaining scheduled delay only while cooling down or waiting to send. */
    public OptionalLong nanosUntilNextSend() {
        if (state != RtpState.COOLDOWN && state != RtpState.WAITING_TO_SEND) {
            return OptionalLong.empty();
        }
        long remaining = nextActionAtNanos - clock.nanoTime();
        return OptionalLong.of(Math.max(0, remaining));
    }

    public long commandAttempts() {
        return commandAttempts;
    }

    public long confirmedTeleports() {
        return confirmedTeleports;
    }

    public long deliveredSamples() {
        return deliveredSamples;
    }

    public long failedRtpAttempts() {
        return failedRtpAttempts;
    }

    public long sampleSinkFailures() {
        return sampleSinkFailures;
    }

    public RtpFailureReason lastFailureReason() {
        return lastFailureReason;
    }

    public RtpStopReason lastStopReason() {
        return lastStopReason;
    }

    public Optional<String> lastErrorMessage() {
        return Optional.ofNullable(lastErrorMessage);
    }

    public Optional<RtpSampleResult> lastSampleResult() {
        return Optional.ofNullable(lastSampleResult);
    }

    private static final class PendingRequest {
        private RtpRequest request;
        private PositionObservation lastCandidate;
        private long stabilizationStartedAtNanos;
        private int stabilizationTicks;
        private int stableTicks;
        private boolean sampleAttempted;

        private PendingRequest(RtpRequest request) {
            this.request = request;
        }

        private void beginStabilization(PositionObservation candidate, long now) {
            lastCandidate = candidate;
            stabilizationStartedAtNanos = now;
            stabilizationTicks = 0;
            stableTicks = 1;
        }

        private void clearStabilization() {
            lastCandidate = null;
            stabilizationStartedAtNanos = 0;
            stabilizationTicks = 0;
            stableTicks = 0;
        }

        private void observeStabilization(
                PositionObservation candidate,
                TeleportDetector detector,
                double toleranceBlocks
        ) {
            if (lastCandidate == null) {
                throw new IllegalStateException("Stabilization has not started");
            }
            stabilizationTicks++;
            stableTicks = detector.isStable(lastCandidate, candidate, toleranceBlocks)
                    ? stableTicks + 1
                    : 1;
            lastCandidate = candidate;
        }
    }
}
