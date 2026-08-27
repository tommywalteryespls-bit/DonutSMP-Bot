package com.donutsmp.rtpmapper.mining;

import com.donutsmp.rtpmapper.automation.AutomationCoordinator;
import com.donutsmp.rtpmapper.automation.AutomationMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningControllerTest {
    private static final MiningSettings SETTINGS = new MiningSettings(
            List.of("diamond_ore", "minecraft:deepslate_diamond_ore"),
            12,
            MiningSettings.minutesToNanos(1)
    );

    @Test
    void startFailsClosedUntilEveryPrerequisiteIsSatisfied() {
        Harness harness = new Harness();

        assertEquals(MiningStartResult.ENVIRONMENT_UNAVAILABLE,
                harness.controller.start(SETTINGS, MiningEnvironment.unavailable()));
        assertEquals(MiningStartResult.SERVER_NOT_ALLOWED,
                harness.controller.start(SETTINGS, harness.environment(false)));
        assertEquals(0, harness.backend.availabilityChecks);

        harness.backend.available = false;
        assertEquals(MiningStartResult.BARITONE_UNAVAILABLE,
                harness.controller.start(SETTINGS, harness.environment(true)));
        assertEquals(AutomationMode.IDLE, harness.coordinator.mode());

        harness.backend.available = true;
        AutomationCoordinator.Lease rtpLease = harness.coordinator
                .tryAcquire(AutomationMode.RTP_MAPPING)
                .orElseThrow();
        assertEquals(MiningStartResult.AUTOMATION_BUSY,
                harness.controller.start(SETTINGS, harness.environment(true)));
        assertEquals(0, harness.backend.startCalls);
        assertEquals(AutomationMode.RTP_MAPPING, harness.coordinator.mode());
        assertTrue(harness.coordinator.release(rtpLease));
    }

    @Test
    void successfulStartSnapshotsSettingsAndIsIdempotent() {
        Harness harness = new Harness();

        assertEquals(MiningStartResult.STARTED,
                harness.controller.start(SETTINGS, harness.environment(true)));

        assertTrue(harness.controller.isRunning());
        assertEquals(MiningState.RUNNING, harness.controller.state());
        assertEquals(MiningStopReason.NONE, harness.controller.lastStopReason());
        assertTrue(harness.controller.lastErrorMessage().isEmpty());
        assertEquals(SETTINGS, harness.controller.activeSettings().orElseThrow());
        assertEquals(SETTINGS.blockIds(), harness.backend.startedBlockIds);
        assertEquals(SETTINGS.quantity(), harness.backend.startedQuantity);
        assertEquals(1, harness.backend.startCalls);
        assertEquals(AutomationMode.BARITONE_MINING, harness.coordinator.mode());
        assertEquals(SETTINGS.timeoutNanos(),
                harness.controller.nanosUntilDeadline().orElseThrow());
        assertEquals(0, harness.controller.elapsedNanos());

        assertEquals(MiningStartResult.ALREADY_RUNNING,
                harness.controller.start(MiningSettings.defaults(), harness.environment(true)));
        assertEquals(1, harness.backend.startCalls);

        harness.clock.advance(Duration.ofSeconds(5).toNanos());
        assertEquals(Duration.ofSeconds(5).toNanos(), harness.controller.elapsedNanos());
        assertEquals(SETTINGS.timeoutNanos() - Duration.ofSeconds(5).toNanos(),
                harness.controller.nanosUntilDeadline().orElseThrow());
    }

    @Test
    void inactiveMineProcessCompletesAndReleasesLease() {
        Harness harness = new Harness();
        harness.start();
        harness.backend.active = false;

        harness.controller.tick(harness.environment(true));

        assertEquals(MiningState.IDLE, harness.controller.state());
        assertEquals(MiningStopReason.COMPLETED, harness.controller.lastStopReason());
        assertFalse(harness.controller.isRunning());
        assertTrue(harness.controller.activeSettings().isEmpty());
        assertTrue(harness.controller.nanosUntilDeadline().isEmpty());
        assertEquals(AutomationMode.IDLE, harness.coordinator.mode());
        assertEquals(1, harness.backend.cancelMineCalls);
        assertEquals(0, harness.backend.cancelEverythingCalls,
                "ordinary completion must not cancel unrelated Baritone processes");
    }

    @Test
    void timeoutCancelsAtTheExactConfiguredDeadline() {
        Harness harness = new Harness();
        harness.start();

        harness.clock.advance(SETTINGS.timeoutNanos() - 1);
        harness.controller.tick(harness.environment(true));
        assertTrue(harness.controller.isRunning());

        harness.clock.advance(1);
        harness.controller.tick(harness.environment(true));

        assertEquals(MiningState.IDLE, harness.controller.state());
        assertEquals(MiningStopReason.TIMEOUT, harness.controller.lastStopReason());
        assertEquals(1, harness.backend.cancelMineCalls);
        assertEquals(0, harness.backend.cancelEverythingCalls);
        assertEquals(AutomationMode.IDLE, harness.coordinator.mode());
    }

    @Test
    void connectionAndPolicyChangesFailClosed() {
        Harness disconnected = new Harness();
        disconnected.start();
        disconnected.controller.tick(MiningEnvironment.unavailable());
        assertEquals(MiningStopReason.DISCONNECTED, disconnected.controller.lastStopReason());
        assertEquals(1, disconnected.backend.cancelMineCalls);
        assertEquals(0, disconnected.backend.cancelEverythingCalls);

        Harness changed = new Harness();
        changed.start();
        changed.controller.tick(MiningEnvironment.ready(true, new Object(), "Different server"));
        assertEquals(MiningStopReason.CONNECTION_CHANGED, changed.controller.lastStopReason());

        Harness revoked = new Harness();
        revoked.start();
        revoked.controller.tick(revoked.environment(false));
        assertEquals(MiningStopReason.SERVER_NOT_ALLOWED, revoked.controller.lastStopReason());

        assertEquals(AutomationMode.IDLE, disconnected.coordinator.mode());
        assertEquals(AutomationMode.IDLE, changed.coordinator.mode());
        assertEquals(AutomationMode.IDLE, revoked.coordinator.mode());
    }

    @Test
    void disconnectCallbackOnlyStopsItsMatchingSession() {
        Harness harness = new Harness();
        harness.start();

        assertFalse(harness.controller.onDisconnected(new Object()));
        assertTrue(harness.controller.isRunning());
        assertTrue(harness.controller.onDisconnected(harness.connectionIdentity));
        assertFalse(harness.controller.isRunning());
        assertEquals(MiningStopReason.DISCONNECTED, harness.controller.lastStopReason());
        assertFalse(harness.controller.onDisconnected(harness.connectionIdentity));
    }

    @Test
    void manualAndEmergencyStopsHaveDistinctCancellationPaths() {
        Harness manual = new Harness();
        assertFalse(manual.controller.stop(MiningStopReason.USER_REQUEST));
        assertThrows(IllegalArgumentException.class,
                () -> manual.controller.stop(MiningStopReason.NONE));
        assertThrows(IllegalArgumentException.class,
                () -> manual.controller.stop(MiningStopReason.COMPLETED));
        manual.start();

        assertTrue(manual.controller.stop(MiningStopReason.USER_REQUEST));
        assertEquals(MiningStopReason.USER_REQUEST, manual.controller.lastStopReason());
        assertEquals(1, manual.backend.cancelMineCalls);
        assertEquals(0, manual.backend.cancelEverythingCalls);

        Harness emergency = new Harness();
        emergency.start();
        assertTrue(emergency.controller.emergencyStop());
        assertEquals(MiningStopReason.EMERGENCY_STOP, emergency.controller.lastStopReason());
        assertEquals(0, emergency.backend.cancelMineCalls);
        assertEquals(1, emergency.backend.cancelEverythingCalls);
        assertFalse(emergency.controller.emergencyStop());
        assertEquals(2, emergency.backend.cancelEverythingCalls,
                "an idle emergency stop must still attempt to stop external Baritone work");
    }

    @Test
    void backendFailuresNeverLeakTheAutomationLease() {
        Harness availabilityFailure = new Harness();
        availabilityFailure.backend.availabilityFailure = new IllegalStateException("API lookup failed");
        assertEquals(MiningStartResult.START_FAILED,
                availabilityFailure.controller.start(SETTINGS, availabilityFailure.environment(true)));
        assertEquals(MiningStopReason.BACKEND_ERROR,
                availabilityFailure.controller.lastStopReason());
        assertEquals("API lookup failed",
                availabilityFailure.controller.lastErrorMessage().orElseThrow());
        assertEquals(AutomationMode.IDLE, availabilityFailure.coordinator.mode());

        Harness startFailure = new Harness();
        startFailure.backend.startFailure = new IllegalStateException("mine command failed");
        assertEquals(MiningStartResult.START_FAILED,
                startFailure.controller.start(SETTINGS, startFailure.environment(true)));
        assertEquals(MiningStopReason.BACKEND_ERROR, startFailure.controller.lastStopReason());
        assertEquals("mine command failed", startFailure.controller.lastErrorMessage().orElseThrow());
        assertEquals(1, startFailure.backend.cancelMineCalls);
        assertEquals(0, startFailure.backend.cancelEverythingCalls);
        assertEquals(AutomationMode.IDLE, startFailure.coordinator.mode());

        Harness statusFailure = new Harness();
        statusFailure.start();
        statusFailure.backend.statusFailure = new LinkageError("API changed");
        statusFailure.controller.tick(statusFailure.environment(true));
        assertEquals(MiningStopReason.BACKEND_ERROR, statusFailure.controller.lastStopReason());
        assertEquals("API changed", statusFailure.controller.lastErrorMessage().orElseThrow());
        assertEquals(1, statusFailure.backend.cancelMineCalls);
        assertEquals(0, statusFailure.backend.cancelEverythingCalls);
        assertEquals(AutomationMode.IDLE, statusFailure.coordinator.mode());
    }

    @Test
    void failedProcessCancelReleasesLeaseWithoutCancellingUnrelatedProcesses() {
        Harness harness = new Harness();
        harness.start();
        harness.backend.cancelMineFailure = new IllegalStateException("process cancel failed");

        assertTrue(harness.controller.stop(MiningStopReason.USER_REQUEST));

        assertEquals(1, harness.backend.cancelMineCalls);
        assertEquals(0, harness.backend.cancelEverythingCalls);
        assertEquals(MiningState.IDLE, harness.controller.state());
        assertEquals(MiningStopReason.BACKEND_ERROR, harness.controller.lastStopReason());
        assertEquals("process cancel failed", harness.controller.lastErrorMessage().orElseThrow());
        assertEquals(AutomationMode.IDLE, harness.coordinator.mode());
    }

    @Test
    void nanoTimeRolloverDoesNotShortenTheTimeout() {
        Harness harness = new Harness(Long.MAX_VALUE - 1_000);
        harness.start();

        harness.clock.advance(Duration.ofSeconds(1).toNanos());
        harness.controller.tick(harness.environment(true));

        assertTrue(harness.controller.isRunning());
        assertEquals(Duration.ofSeconds(59).toNanos(),
                harness.controller.nanosUntilDeadline().orElseThrow());

        harness.clock.advance(Duration.ofSeconds(59).toNanos());
        harness.controller.tick(harness.environment(true));
        assertEquals(MiningStopReason.TIMEOUT, harness.controller.lastStopReason());
    }

    @Test
    void readyEnvironmentRequiresAConnectionIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new MiningEnvironment(true, true, null, "Broken"));
        assertEquals("", new MiningEnvironment(false, false, null, null).serverDescription());
    }

    private static final class Harness {
        private final FakeBackend backend = new FakeBackend();
        private final MutableClock clock;
        private final AutomationCoordinator coordinator = new AutomationCoordinator();
        private final MiningController controller;
        private final Object connectionIdentity = new Object();

        private Harness() {
            this(0);
        }

        private Harness(long initialNanos) {
            clock = new MutableClock(initialNanos);
            controller = new MiningController(backend, clock::nanoTime, coordinator);
        }

        private void start() {
            assertEquals(MiningStartResult.STARTED,
                    controller.start(SETTINGS, environment(true)));
        }

        private MiningEnvironment environment(boolean allowed) {
            return MiningEnvironment.ready(allowed, connectionIdentity, "Test world");
        }
    }

    private static final class MutableClock {
        private long nanos;

        private MutableClock(long nanos) {
            this.nanos = nanos;
        }

        private long nanoTime() {
            return nanos;
        }

        private void advance(long amount) {
            nanos += amount;
        }
    }

    private static final class FakeBackend implements MiningBackend {
        private boolean available = true;
        private boolean active = true;
        private Throwable availabilityFailure;
        private Throwable startFailure;
        private Throwable statusFailure;
        private Throwable cancelMineFailure;
        private Throwable cancelEverythingFailure;
        private int availabilityChecks;
        private int startCalls;
        private int cancelMineCalls;
        private int cancelEverythingCalls;
        private List<String> startedBlockIds = List.of();
        private int startedQuantity;

        @Override
        public boolean available() {
            availabilityChecks++;
            throwIfConfigured(availabilityFailure);
            return available;
        }

        @Override
        public void start(List<String> blockIds, int quantity) {
            startCalls++;
            startedBlockIds = List.copyOf(blockIds);
            startedQuantity = quantity;
            throwIfConfigured(startFailure);
            active = true;
        }

        @Override
        public boolean isMineProcessActive() {
            throwIfConfigured(statusFailure);
            return active;
        }

        @Override
        public void cancelMine() {
            cancelMineCalls++;
            active = false;
            throwIfConfigured(cancelMineFailure);
        }

        @Override
        public void cancelEverything() {
            cancelEverythingCalls++;
            active = false;
            throwIfConfigured(cancelEverythingFailure);
        }

        private static void throwIfConfigured(Throwable failure) {
            if (failure instanceof RuntimeException exception) {
                throw exception;
            }
            if (failure instanceof LinkageError error) {
                throw error;
            }
        }
    }
}
