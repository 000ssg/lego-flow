package ssg.legoflow.blocks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorStateTest {

    @Test
    void testIdleCanTransitionToConnecting() {
        assertThat(ProcessorState.IDLE.canTransitionTo(ProcessorState.CONNECTING)).isTrue();
    }

    @Test
    void testIdleCanTransitionToReady() {
        assertThat(ProcessorState.IDLE.canTransitionTo(ProcessorState.READY)).isTrue();
    }

    @Test
    void testIdleCanTransitionToStopped() {
        assertThat(ProcessorState.IDLE.canTransitionTo(ProcessorState.STOPPED)).isTrue();
    }

    @Test
    void testIdleCannotTransitionToPaused() {
        assertThat(ProcessorState.IDLE.canTransitionTo(ProcessorState.PAUSED)).isFalse();
    }

    @Test
    void testIdleCannotTransitionToFailed() {
        assertThat(ProcessorState.IDLE.canTransitionTo(ProcessorState.FAILED)).isFalse();
    }

    @Test
    void testReadyCanTransitionToPaused() {
        assertThat(ProcessorState.READY.canTransitionTo(ProcessorState.PAUSED)).isTrue();
    }

    @Test
    void testReadyCanTransitionToFailed() {
        assertThat(ProcessorState.READY.canTransitionTo(ProcessorState.FAILED)).isTrue();
    }

    @Test
    void testReadyCanTransitionToStopped() {
        assertThat(ProcessorState.READY.canTransitionTo(ProcessorState.STOPPED)).isTrue();
    }

    @Test
    void testPausedCanTransitionToReady() {
        assertThat(ProcessorState.PAUSED.canTransitionTo(ProcessorState.READY)).isTrue();
    }

    @Test
    void testFailedCanTransitionToConnecting() {
        assertThat(ProcessorState.FAILED.canTransitionTo(ProcessorState.CONNECTING)).isTrue();
    }

    @Test
    void testFailedCanTransitionToReady() {
        assertThat(ProcessorState.FAILED.canTransitionTo(ProcessorState.READY)).isTrue();
    }

    @Test
    void testStoppedCannotTransitionAnywhere() {
        for (var target : ProcessorState.values()) {
            assertThat(ProcessorState.STOPPED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void testConnectingCanTransitionToReady() {
        assertThat(ProcessorState.CONNECTING.canTransitionTo(ProcessorState.READY)).isTrue();
    }

    @Test
    void testConnectingCanTransitionToFailed() {
        assertThat(ProcessorState.CONNECTING.canTransitionTo(ProcessorState.FAILED)).isTrue();
    }
}
