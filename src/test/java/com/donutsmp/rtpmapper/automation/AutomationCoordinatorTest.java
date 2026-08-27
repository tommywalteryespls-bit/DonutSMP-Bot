package com.donutsmp.rtpmapper.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationCoordinatorTest {
    @Test
    void grantsExactlyOneAutomationModeAtATime() {
        AutomationCoordinator coordinator = new AutomationCoordinator();

        assertEquals(AutomationMode.IDLE, coordinator.mode());
        AutomationCoordinator.Lease rtpLease = coordinator.tryAcquire(AutomationMode.RTP_MAPPING)
                .orElseThrow();

        assertEquals(AutomationMode.RTP_MAPPING, coordinator.mode());
        assertTrue(coordinator.tryAcquire(AutomationMode.RTP_MAPPING).isEmpty());
        assertTrue(coordinator.tryAcquire(AutomationMode.BARITONE_MINING).isEmpty());
        assertTrue(coordinator.release(rtpLease));
        assertEquals(AutomationMode.IDLE, coordinator.mode());

        AutomationCoordinator.Lease miningLease = coordinator.tryAcquire(AutomationMode.BARITONE_MINING)
                .orElseThrow();
        assertEquals(AutomationMode.BARITONE_MINING, coordinator.mode());
        assertTrue(coordinator.release(miningLease));
    }

    @Test
    void staleOrMismatchedLeaseCannotReleaseANewerSession() {
        AutomationCoordinator coordinator = new AutomationCoordinator();
        AutomationCoordinator.Lease first = coordinator.tryAcquire(AutomationMode.RTP_MAPPING)
                .orElseThrow();

        assertFalse(coordinator.release(null));
        assertFalse(coordinator.release(new AutomationCoordinator.Lease(
                AutomationMode.BARITONE_MINING,
                first.generation()
        )));
        assertEquals(AutomationMode.RTP_MAPPING, coordinator.mode());
        assertTrue(coordinator.release(first));

        AutomationCoordinator.Lease second = coordinator.tryAcquire(AutomationMode.RTP_MAPPING)
                .orElseThrow();
        assertTrue(second.generation() > first.generation());
        assertFalse(coordinator.release(first));
        assertEquals(AutomationMode.RTP_MAPPING, coordinator.mode());
        assertTrue(coordinator.release(second));
        assertFalse(coordinator.release(second));
    }

    @Test
    void invalidLeaseRequestsAreRejected() {
        AutomationCoordinator coordinator = new AutomationCoordinator();

        assertThrows(NullPointerException.class, () -> coordinator.tryAcquire(null));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.tryAcquire(AutomationMode.IDLE));
        assertThrows(NullPointerException.class,
                () -> new AutomationCoordinator.Lease(null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AutomationCoordinator.Lease(AutomationMode.RTP_MAPPING, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AutomationCoordinator.Lease(AutomationMode.IDLE, 1));
        assertEquals(AutomationMode.IDLE, coordinator.mode());
    }
}
