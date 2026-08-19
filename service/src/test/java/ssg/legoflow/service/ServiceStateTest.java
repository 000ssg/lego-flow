package ssg.legoflow.service;

import ssg.legoflow.blocks.ProcessorState;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ServiceStateTest {

    // ── Enum values ────────────────────────────────────────

    @Test
    void testIdleToProcessorState() {
        assertThat(ServiceState.IDLE.toProcessorState()).isEqualTo(ProcessorState.IDLE);
    }

    @Test
    void testReadyToProcessorState() {
        assertThat(ServiceState.READY.toProcessorState()).isEqualTo(ProcessorState.READY);
    }

    @Test
    void testFailedToProcessorState() {
        assertThat(ServiceState.FAILED.toProcessorState()).isEqualTo(ProcessorState.FAILED);
    }

    @Test
    void testStoppedToProcessorState() {
        assertThat(ServiceState.STOPPED.toProcessorState()).isEqualTo(ProcessorState.STOPPED);
    }

    // ── Valid transitions from IDLE ────────────────────────

    @Test
    void testIdleCanTransitionToConnectingTransport() {
        assertThat(ServiceState.IDLE.canTransitionTo(ServiceState.CONNECTING_TRANSPORT)).isTrue();
    }

    @Test
    void testIdleCanTransitionToReady() {
        assertThat(ServiceState.IDLE.canTransitionTo(ServiceState.READY)).isTrue();
    }

    @Test
    void testIdleCanTransitionToStopped() {
        assertThat(ServiceState.IDLE.canTransitionTo(ServiceState.STOPPED)).isTrue();
    }

    @Test
    void testIdleCannotTransitionToFailed() {
        assertThat(ServiceState.IDLE.canTransitionTo(ServiceState.FAILED)).isFalse();
    }

    // ── CONNECTING_TRANSPORT transitions ───────────────────

    @Test
    void testConnectingTransportCanTransitionToAuthenticating() {
        assertThat(ServiceState.CONNECTING_TRANSPORT.canTransitionTo(ServiceState.AUTHENTICATING)).isTrue();
    }

    @Test
    void testConnectingTransportCanTransitionToReady() {
        assertThat(ServiceState.CONNECTING_TRANSPORT.canTransitionTo(ServiceState.READY)).isTrue();
    }

    @Test
    void testConnectingTransportCanTransitionToFailed() {
        assertThat(ServiceState.CONNECTING_TRANSPORT.canTransitionTo(ServiceState.FAILED)).isTrue();
    }

    // ── AUTHENTICATING transitions ────────────────────────

    @Test
    void testAuthenticatingCanTransitionToReady() {
        assertThat(ServiceState.AUTHENTICATING.canTransitionTo(ServiceState.READY)).isTrue();
    }

    @Test
    void testAuthenticatingCanTransitionToFailed() {
        assertThat(ServiceState.AUTHENTICATING.canTransitionTo(ServiceState.FAILED)).isTrue();
    }

    // ── READY transitions ─────────────────────────────────

    @Test
    void testReadyCanTransitionToPaused() {
        assertThat(ServiceState.READY.canTransitionTo(ServiceState.PAUSED)).isTrue();
    }

    @Test
    void testReadyCanTransitionToDraining() {
        assertThat(ServiceState.READY.canTransitionTo(ServiceState.DRAINING)).isTrue();
    }

    @Test
    void testReadyCanTransitionToDisconnecting() {
        assertThat(ServiceState.READY.canTransitionTo(ServiceState.DISCONNECTING)).isTrue();
    }

    @Test
    void testReadyCanTransitionToFailed() {
        assertThat(ServiceState.READY.canTransitionTo(ServiceState.FAILED)).isTrue();
    }

    // ── PAUSED transitions ────────────────────────────────

    @Test
    void testPausedCanTransitionToReady() {
        assertThat(ServiceState.PAUSED.canTransitionTo(ServiceState.READY)).isTrue();
    }

    @Test
    void testPausedCanTransitionToDraining() {
        assertThat(ServiceState.PAUSED.canTransitionTo(ServiceState.DRAINING)).isTrue();
    }

    // ── DRAINING transitions ──────────────────────────────

    @Test
    void testDrainingCanTransitionToDisconnecting() {
        assertThat(ServiceState.DRAINING.canTransitionTo(ServiceState.DISCONNECTING)).isTrue();
    }

    // ── DISCONNECTING transitions ─────────────────────────

    @Test
    void testDisconnectingCanTransitionToIdle() {
        assertThat(ServiceState.DISCONNECTING.canTransitionTo(ServiceState.IDLE)).isTrue();
    }

    // ── FAILED transitions ────────────────────────────────

    @Test
    void testFailedCanTransitionToConnectingTransport() {
        assertThat(ServiceState.FAILED.canTransitionTo(ServiceState.CONNECTING_TRANSPORT)).isTrue();
    }

    @Test
    void testFailedCanTransitionToIdle() {
        assertThat(ServiceState.FAILED.canTransitionTo(ServiceState.IDLE)).isTrue();
    }

    // ── STOPPED - no transitions allowed ───────────────────

    @Test
    void testStoppedCannotTransitionToAnyState() {
        for (ServiceState target : ServiceState.values()) {
            assertThat(ServiceState.STOPPED.canTransitionTo(target))
                    .as("STOPPED cannot transition to %s", target)
                    .isFalse();
        }
    }

    // ── Values count ───────────────────────────────────────

    @Test
    void testAllStatesPresent() {
        assertThat(ServiceState.values()).hasSize(9);
    }
}
