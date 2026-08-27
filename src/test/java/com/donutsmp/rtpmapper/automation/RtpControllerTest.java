package com.donutsmp.rtpmapper.automation;

import com.donutsmp.rtpmapper.region.RtpRegion;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtpControllerTest {
    private static final PositionObservation BASELINE = position(0, 64, 0);
    private static final PositionObservation DESTINATION = position(2_000, 70, -1_000);

    @Test
    void normalRtpSendsOnceRecordsOnceAndStartsCooldownAtRecordTime() {
        Harness harness = new Harness(settings(5, 20, 3, 2, 5, 0.25));

        harness.startAndSend();
        assertEquals(RtpState.WAITING_FOR_TELEPORT, harness.controller.state());
        assertEquals(List.of(1L), harness.commands);

        harness.tick(DESTINATION);
        assertEquals(RtpState.WAITING_FOR_STABILIZATION, harness.controller.state());
        harness.tick(DESTINATION);
        harness.tick(DESTINATION);
        assertTrue(harness.samples.isEmpty());
        harness.tick(DESTINATION);

        assertEquals(RtpState.COOLDOWN, harness.controller.state());
        assertEquals(1, harness.samples.size());
        assertEquals(1, harness.controller.confirmedTeleports());
        assertEquals(1, harness.controller.deliveredSamples());
        assertEquals(1, harness.controller.commandAttempts());
        assertEquals(1, harness.samples.getFirst().requestNumber());

        harness.clock.advance(Duration.ofSeconds(5).minusNanos(1));
        harness.tick(DESTINATION);
        assertEquals(1, harness.commands.size());
        assertEquals(RtpState.COOLDOWN, harness.controller.state());

        harness.clock.advanceNanos(1);
        harness.tick(DESTINATION);
        assertEquals(RtpState.WAITING_TO_SEND, harness.controller.state());
        assertEquals(1, harness.commands.size());

        harness.tick(DESTINATION);
        assertEquals(List.of(1L, 2L), harness.commands);
    }

    @Test
    void delayedTeleportDoesNotConsumeCooldownWhileWaiting() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.startAndSend();

        harness.clock.advance(Duration.ofSeconds(3));
        harness.tick(BASELINE);
        assertEquals(1, harness.commands.size());
        assertTrue(harness.samples.isEmpty());

        harness.tick(DESTINATION);
        harness.tick(DESTINATION);
        assertEquals(1, harness.samples.size());
        long recordedAt = harness.clock.nanoTime();

        harness.clock.advance(Duration.ofSeconds(2));
        harness.tick(DESTINATION);
        assertEquals(RtpState.COOLDOWN, harness.controller.state());
        assertEquals(1, harness.commands.size(), "five seconds from send is not the retry deadline");

        harness.clock.advance(Duration.ofSeconds(3));
        harness.tick(DESTINATION);
        assertEquals(recordedAt + Duration.ofSeconds(5).toNanos(), harness.clock.nanoTime());
        assertEquals(RtpState.WAITING_TO_SEND, harness.controller.state());
    }

    @Test
    void sinkAndFailedSenderWorkDoNotConsumePostCompletionCooldown() {
        Harness recorded = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        recorded.sinkWorkNanos = Duration.ofSeconds(2).toNanos();
        recorded.startAndSend();
        recorded.tick(DESTINATION);
        recorded.tick(DESTINATION);
        long recordCompletedAt = recorded.clock.nanoTime();

        recorded.clock.advance(Duration.ofSeconds(5).minusNanos(1));
        recorded.tick(DESTINATION);
        assertEquals(RtpState.COOLDOWN, recorded.controller.state());
        recorded.clock.advanceNanos(1);
        recorded.tick(DESTINATION);
        assertEquals(recordCompletedAt + Duration.ofSeconds(5).toNanos(), recorded.clock.nanoTime());
        assertEquals(RtpState.WAITING_TO_SEND, recorded.controller.state());

        Harness failedSend = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        failedSend.senderWorkNanos = Duration.ofSeconds(2).toNanos();
        failedSend.sendFailure = new IllegalStateException("delayed network failure");
        assertEquals(RtpStartResult.STARTED, failedSend.controller.start(failedSend.environment(BASELINE)));
        failedSend.tick(BASELINE);
        long failureCompletedAt = failedSend.clock.nanoTime();

        failedSend.clock.advance(Duration.ofSeconds(5).minusNanos(1));
        failedSend.tick(BASELINE);
        assertEquals(RtpState.COOLDOWN, failedSend.controller.state());
        failedSend.clock.advanceNanos(1);
        failedSend.tick(BASELINE);
        assertEquals(failureCompletedAt + Duration.ofSeconds(5).toNanos(), failedSend.clock.nanoTime());
        assertEquals(RtpState.WAITING_TO_SEND, failedSend.controller.state());

        Harness successfulSend = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        successfulSend.senderWorkNanos = Duration.ofSeconds(2).toNanos();
        successfulSend.startAndSend();
        RtpRequest sentRequest = successfulSend.controller.pendingRequest().orElseThrow();
        assertEquals(Duration.ofSeconds(2).toNanos(), sentRequest.sentAtNanos());
        assertEquals(Duration.ofSeconds(22).toNanos(), sentRequest.teleportDeadlineNanos());

        successfulSend.clock.advance(Duration.ofSeconds(20).minusNanos(1));
        successfulSend.tick(BASELINE);
        assertEquals(RtpState.WAITING_FOR_TELEPORT, successfulSend.controller.state());
        successfulSend.clock.advanceNanos(1);
        successfulSend.tick(BASELINE);
        assertEquals(RtpState.COOLDOWN, successfulSend.controller.state());
    }

    @Test
    void failedTeleportTimesOutThenUsesNormalCooldown() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.startAndSend();

        harness.clock.advance(Duration.ofSeconds(20));
        harness.tick(BASELINE);

        assertEquals(RtpState.COOLDOWN, harness.controller.state());
        assertEquals(RtpFailureReason.TELEPORT_TIMEOUT, harness.controller.lastFailureReason());
        assertEquals(1, harness.controller.failedRtpAttempts());
        assertTrue(harness.samples.isEmpty());

        harness.clock.advance(Duration.ofSeconds(5));
        harness.tick(BASELINE);
        assertEquals(RtpState.WAITING_TO_SEND, harness.controller.state());
        harness.tick(BASELINE);
        assertEquals(List.of(1L, 2L), harness.commands);
    }

    @Test
    void aTeleportObservedExactlyAtTimeoutWins() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.startAndSend();

        harness.clock.advance(Duration.ofSeconds(20));
        harness.tick(DESTINATION);

        assertEquals(RtpState.WAITING_FOR_STABILIZATION, harness.controller.state());
        assertEquals(0, harness.controller.failedRtpAttempts());
    }

    @Test
    void multipleCorrectionsAreDebouncedAndCannotDuplicateSample() {
        Harness harness = new Harness(settings(5, 20, 3, 3, 5, 0.25));
        harness.startAndSend();
        harness.tick(position(2_000, 70, 0));
        harness.tick(position(2_100, 70, 0));
        harness.tick(position(2_200, 70, 0));
        harness.tick(position(2_200, 70, 0));
        assertTrue(harness.samples.isEmpty());
        harness.tick(position(2_200, 70, 0));

        assertEquals(1, harness.samples.size());
        for (int i = 0; i < 100; i++) {
            harness.tick(position(2_200 + (i % 2) * 0.1, 70, 0));
        }

        assertEquals(1, harness.commands.size());
        assertEquals(1, harness.samples.size());
        assertEquals(1, harness.samples.stream().map(RtpSampleResult::requestNumber).distinct().count());
    }

    @Test
    void dimensionChangeIsDetectedAndRecorded() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.startAndSend();
        PositionObservation nether = new PositionObservation(0, 64, 0, "minecraft:the_nether");

        harness.tick(nether);
        harness.tick(nether);

        assertEquals(1, harness.samples.size());
        assertEquals("minecraft:the_nether", harness.samples.getFirst().dimension());
        assertEquals(0.0, harness.samples.getFirst().horizontalDistanceFromBaseline());
    }

    @Test
    void transientJumpThatReturnsToBaselineIsNotRecorded() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.startAndSend();
        harness.tick(DESTINATION);
        assertEquals(RtpState.WAITING_FOR_STABILIZATION, harness.controller.state());

        harness.tick(BASELINE);

        assertEquals(RtpState.WAITING_FOR_TELEPORT, harness.controller.state());
        assertTrue(harness.samples.isEmpty());
        assertTrue(harness.controller.pendingRequest().isPresent());
    }

    @Test
    void neverStableDestinationFailsAtIndependentStabilizationDeadline() {
        Harness harness = new Harness(settings(5, 20, 100, 100, 5, 0.25));
        harness.startAndSend();
        harness.tick(DESTINATION);

        harness.clock.advance(Duration.ofSeconds(5));
        harness.tick(position(2_001, 70, -1_000));

        assertEquals(RtpState.COOLDOWN, harness.controller.state());
        assertEquals(RtpFailureReason.STABILIZATION_TIMEOUT, harness.controller.lastFailureReason());
        assertEquals(1, harness.controller.failedRtpAttempts());
        assertTrue(harness.samples.isEmpty());
    }

    @Test
    void stableDestinationAtExactStabilizationDeadlineWins() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.startAndSend();
        harness.tick(DESTINATION);

        harness.clock.advance(Duration.ofSeconds(5));
        harness.tick(DESTINATION);

        assertEquals(1, harness.samples.size());
        assertEquals(RtpState.COOLDOWN, harness.controller.state());
        assertEquals(0, harness.controller.failedRtpAttempts());
    }

    @Test
    void settingsAreSnapshottedAtSendForDetectionAndCooldown() {
        RtpAttemptSettings original = settings(5, 20, 1, 2, 5, 0.25, RtpRegion.ASIA);
        Harness harness = new Harness(original);
        harness.startAndSend();
        assertEquals(original, harness.controller.pendingRequest().orElseThrow().settings());
        assertEquals(List.of(RtpRegion.ASIA), harness.commandRegions,
                "the sender must receive the request's snapshotted region");
        harness.settings.set(new RtpAttemptSettings(
                Duration.ofSeconds(1).toNanos(),
                10,
                Duration.ofSeconds(1).toNanos(),
                0,
                1,
                Duration.ofSeconds(1).toNanos(),
                10,
                false,
                RtpRegion.EU_WEST
        ));

        harness.tick(position(100, 64, 0));
        assertEquals(RtpState.WAITING_FOR_TELEPORT, harness.controller.state(),
                "new lower threshold must not affect the pending request");

        PositionObservation farEnough = position(1_000, 64, 0);
        harness.tick(farEnough);
        harness.tick(farEnough);
        assertEquals(1, harness.samples.size());

        harness.clock.advance(Duration.ofSeconds(1));
        harness.tick(farEnough);
        assertEquals(RtpState.COOLDOWN, harness.controller.state(),
                "pending request must retain its original five second cooldown");
        assertTrue(harness.controller.lastSampleResult().isPresent());
        assertTrue(harness.controller.lastSampleResult().orElseThrow().storeYCoordinate(),
                "Store-Y must retain the value captured for the pending request");
        assertEquals(RtpRegion.ASIA, harness.controller.lastSampleResult().orElseThrow().requestedRegion(),
                "an in-flight settings change must not relabel the completed sample");

        harness.clock.advance(Duration.ofSeconds(4));
        harness.tick(farEnough);
        assertEquals(RtpState.WAITING_TO_SEND, harness.controller.state());
        harness.tick(farEnough);
        assertEquals(List.of(RtpRegion.ASIA, RtpRegion.EU_WEST), harness.commandRegions,
                "the next request should use the newly selected region");
    }

    @Test
    void disconnectOrConnectionChangeStopsAndDiscardsPendingRequest() {
        Harness disconnected = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        disconnected.startAndSend();
        disconnected.tick(DESTINATION);
        disconnected.controller.tick(RtpEnvironmentSnapshot.disconnected());

        assertEquals(RtpState.IDLE, disconnected.controller.state());
        assertEquals(RtpStopReason.DISCONNECTED, disconnected.controller.lastStopReason());
        assertTrue(disconnected.controller.pendingRequest().isEmpty());
        assertTrue(disconnected.samples.isEmpty());

        Harness changed = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        changed.startAndSend();
        changed.controller.tick(RtpEnvironmentSnapshot.ready(new Object(), DESTINATION));

        assertEquals(RtpState.IDLE, changed.controller.state());
        assertEquals(RtpStopReason.CONNECTION_CHANGED, changed.controller.lastStopReason());
        assertTrue(changed.samples.isEmpty());
    }

    @Test
    void worldOrPlayerLossStopsImmediately() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.startAndSend();

        harness.controller.tick(new RtpEnvironmentSnapshot(true, true, harness.connectionIdentity, null));

        assertEquals(RtpState.IDLE, harness.controller.state());
        assertEquals(RtpStopReason.ENVIRONMENT_UNAVAILABLE, harness.controller.lastStopReason());
    }

    @Test
    void commandSenderExceptionConsumesAttemptAndNeverAssociatesStrayCorrection() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.sendFailure = new IllegalStateException("network unavailable");

        assertEquals(RtpStartResult.STARTED, harness.controller.start(harness.environment(BASELINE)));
        harness.tick(BASELINE);

        assertEquals(1, harness.commands.size());
        assertEquals(1, harness.controller.failedRtpAttempts());
        assertEquals(RtpFailureReason.COMMAND_SEND_FAILED, harness.controller.lastFailureReason());
        assertEquals(RtpState.COOLDOWN, harness.controller.state());

        harness.tick(DESTINATION);
        assertTrue(harness.samples.isEmpty());
        assertEquals(1, harness.commands.size());
    }

    @Test
    void sinkExceptionIsNeverRetriedBecauseAcceptanceIsAmbiguous() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.sinkFailure = new IllegalStateException("disk queue failed after add");
        harness.startAndSend();
        harness.tick(DESTINATION);
        harness.tick(DESTINATION);

        assertEquals(1, harness.sinkInvocations);
        assertEquals(1, harness.controller.confirmedTeleports());
        assertEquals(0, harness.controller.deliveredSamples());
        assertEquals(1, harness.controller.sampleSinkFailures());
        assertEquals(0, harness.controller.failedRtpAttempts());
        assertEquals(RtpFailureReason.SAMPLE_SINK_FAILED, harness.controller.lastFailureReason());
        assertEquals(RtpState.COOLDOWN, harness.controller.state());

        for (int i = 0; i < 20; i++) {
            harness.tick(DESTINATION);
        }
        assertEquals(1, harness.sinkInvocations);
    }

    @Test
    void startIsValidatedAndIdempotent() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));

        assertEquals(RtpStartResult.NOT_CONNECTED,
                harness.controller.start(RtpEnvironmentSnapshot.disconnected()));
        assertEquals(RtpStartResult.SERVER_NOT_ALLOWED,
                harness.controller.start(new RtpEnvironmentSnapshot(true, false,
                        harness.connectionIdentity, BASELINE)));
        assertEquals(RtpStartResult.POSITION_UNAVAILABLE,
                harness.controller.start(new RtpEnvironmentSnapshot(true, true,
                        harness.connectionIdentity, null)));
        assertEquals(RtpStartResult.STARTED, harness.controller.start(harness.environment(BASELINE)));
        assertEquals(RtpStartResult.ALREADY_RUNNING, harness.controller.start(harness.environment(BASELINE)));

        harness.tick(BASELINE);
        assertEquals(1, harness.commands.size());
    }

    @Test
    void explicitStopPreventsLateTeleportFromBeingRecorded() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.startAndSend();

        harness.controller.stop();
        harness.tick(DESTINATION);

        assertEquals(RtpState.IDLE, harness.controller.state());
        assertEquals(RtpStopReason.USER_REQUEST, harness.controller.lastStopReason());
        assertTrue(harness.samples.isEmpty());
        assertEquals(1, harness.commands.size());
    }

    @Test
    void coordinateGuardStopAfterDeliveryPreservesTriggeringSample() {
        RtpAttemptSettings guardedSettings = settingsWithCenterGuard(3_000);
        Harness harness = new Harness(guardedSettings);
        harness.startAndSend();
        harness.tick(DESTINATION);

        long deliveredBefore = harness.controller.deliveredSamples();
        RtpAttemptSettings completedAttemptSettings = harness.controller.pendingRequest()
                .orElseThrow()
                .settings();
        harness.tick(DESTINATION);

        assertEquals(1, harness.samples.size());
        assertEquals(1, harness.controller.deliveredSamples());
        assertEquals(RtpState.COOLDOWN, harness.controller.state());

        RtpStopReason reason = CoordinateStopGuard.stopAfterNewDelivery(
                harness.controller,
                deliveredBefore,
                completedAttemptSettings
        );

        assertEquals(RtpStopReason.CENTER_GUARD_REACHED, reason);
        assertEquals(RtpState.IDLE, harness.controller.state());
        assertEquals(RtpStopReason.CENTER_GUARD_REACHED, harness.controller.lastStopReason());
        assertEquals(1, harness.samples.size(), "the settled landing must remain recorded");

        harness.clock.advance(Duration.ofMinutes(1));
        harness.tick(DESTINATION);
        assertEquals(1, harness.commands.size(), "a guard stop must not send another RTP command");
    }

    @Test
    void invalidSettingsStopWithoutSendingACommand() {
        Harness harness = new Harness(settings(5, 20, 1, 2, 5, 0.25));
        harness.settingsFailure = new IllegalStateException("invalid config");

        assertEquals(RtpStartResult.STARTED, harness.controller.start(harness.environment(BASELINE)));
        harness.tick(BASELINE);

        assertEquals(RtpState.IDLE, harness.controller.state());
        assertEquals(RtpStopReason.CONFIGURATION_ERROR, harness.controller.lastStopReason());
        assertEquals(0, harness.controller.commandAttempts());
        assertTrue(harness.commands.isEmpty());
        assertEquals("invalid config", harness.controller.lastErrorMessage().orElseThrow());
    }

    @Test
    void generatedTraceNeverProducesMoreSamplesThanCommandsOrDuplicatesRequestIds() {
        Harness harness = new Harness(settings(1, 2, 1, 2, 1, 0.25));
        harness.startAndSend();

        long seed = 0x5EEDL;
        for (int tick = 0; tick < 2_000; tick++) {
            seed = seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
            boolean jump = (seed >>> 60) == 0;
            PositionObservation observation = jump
                    ? position((seed & 0xFFFF) + 2_000, 70, -((seed >>> 16) & 0xFFFF))
                    : BASELINE;
            harness.clock.advance(Duration.ofMillis(50));
            harness.tick(observation);

            assertTrue(harness.sinkInvocations <= harness.commands.size());
            assertEquals(
                    harness.samples.size(),
                    harness.samples.stream().map(RtpSampleResult::requestNumber).distinct().count()
            );
        }
    }

    private static RtpAttemptSettings settings(
            long cooldownSeconds,
            long teleportTimeoutSeconds,
            int minimumStabilizationTicks,
            int requiredStableTicks,
            long maximumStabilizationSeconds,
            double tolerance
    ) {
        return new RtpAttemptSettings(
                Duration.ofSeconds(cooldownSeconds).toNanos(),
                1_000,
                Duration.ofSeconds(teleportTimeoutSeconds).toNanos(),
                minimumStabilizationTicks,
                requiredStableTicks,
                Duration.ofSeconds(maximumStabilizationSeconds).toNanos(),
                tolerance,
                true
        );
    }

    private static RtpAttemptSettings settingsWithCenterGuard(double radiusBlocks) {
        RtpAttemptSettings defaults = settings(5, 20, 1, 2, 5, 0.25);
        return new RtpAttemptSettings(
                defaults.cooldownNanos(),
                defaults.teleportThresholdBlocks(),
                defaults.teleportTimeoutNanos(),
                defaults.minimumStabilizationTicks(),
                defaults.requiredStableTicks(),
                defaults.maximumStabilizationNanos(),
                defaults.stabilityToleranceBlocks(),
                defaults.storeYCoordinate(),
                defaults.requestedRegion(),
                true,
                radiusBlocks,
                false,
                10_000.0
        );
    }

    private static RtpAttemptSettings settings(
            long cooldownSeconds,
            long teleportTimeoutSeconds,
            int minimumStabilizationTicks,
            int requiredStableTicks,
            long maximumStabilizationSeconds,
            double tolerance,
            RtpRegion requestedRegion
    ) {
        return new RtpAttemptSettings(
                Duration.ofSeconds(cooldownSeconds).toNanos(),
                1_000,
                Duration.ofSeconds(teleportTimeoutSeconds).toNanos(),
                minimumStabilizationTicks,
                requiredStableTicks,
                Duration.ofSeconds(maximumStabilizationSeconds).toNanos(),
                tolerance,
                true,
                requestedRegion
        );
    }

    private static PositionObservation position(double x, double y, double z) {
        return new PositionObservation(x, y, z, "minecraft:overworld");
    }

    private static final class Harness {
        private final FakeClock clock = new FakeClock();
        private final Object connectionIdentity = new Object();
        private final AtomicReference<RtpAttemptSettings> settings;
        private final List<Long> commands = new ArrayList<>();
        private final List<RtpRegion> commandRegions = new ArrayList<>();
        private final List<RtpSampleResult> samples = new ArrayList<>();
        private final RtpController controller;
        private RuntimeException sendFailure;
        private RuntimeException sinkFailure;
        private RuntimeException settingsFailure;
        private int sinkInvocations;
        private long senderWorkNanos;
        private long sinkWorkNanos;

        private Harness(RtpAttemptSettings initialSettings) {
            settings = new AtomicReference<>(initialSettings);
            controller = new RtpController(
                    clock,
                    () -> {
                        if (settingsFailure != null) {
                            throw settingsFailure;
                        }
                        return settings.get();
                    },
                    (requestNumber, requestedRegion) -> {
                        commands.add(requestNumber);
                        commandRegions.add(requestedRegion);
                        clock.advanceNanos(senderWorkNanos);
                        if (sendFailure != null) {
                            throw sendFailure;
                        }
                    },
                    result -> {
                        sinkInvocations++;
                        clock.advanceNanos(sinkWorkNanos);
                        if (sinkFailure != null) {
                            throw sinkFailure;
                        }
                        samples.add(result);
                    }
            );
        }

        private void startAndSend() {
            assertEquals(RtpStartResult.STARTED, controller.start(environment(BASELINE)));
            tick(BASELINE);
            assertNotNull(controller.pendingRequest().orElseThrow());
        }

        private void tick(PositionObservation position) {
            controller.tick(environment(position));
        }

        private RtpEnvironmentSnapshot environment(PositionObservation position) {
            return RtpEnvironmentSnapshot.ready(connectionIdentity, position);
        }
    }

    private static final class FakeClock implements RtpClock {
        private long nanos;
        private long millis = 1_800_000_000_000L;

        @Override
        public long nanoTime() {
            return nanos;
        }

        @Override
        public long currentTimeMillis() {
            return millis;
        }

        private void advance(Duration duration) {
            advanceNanos(duration.toNanos());
        }

        private void advanceNanos(long amount) {
            nanos += amount;
            millis += amount / 1_000_000;
        }
    }
}
