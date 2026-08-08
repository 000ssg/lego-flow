package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.blocks.exceptions.StateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatefulProcessorDemoTest {

    private static class StatefulProcessor extends PassthroughProcessor<String> {
        StatefulProcessor() {
            super(String.class);
        }

        void ready() {
            transitionTo(ProcessorState.READY);
        }

        void pause() {
            transitionTo(ProcessorState.PAUSED);
        }

        void resume() {
            transitionTo(ProcessorState.READY);
        }

        void fail() {
            transitionTo(ProcessorState.FAILED);
        }

        void reconnect() {
            transitionTo(ProcessorState.CONNECTING);
        }
    }

    @Test
    void testFullLifecycle() {
        // Given: a stateful processor with a listener tracking transitions
        var processor = new StatefulProcessor();
        var transitions = new ArrayList<ProcessorState>();
        processor.addStateListener((old, newState) -> transitions.add(newState));

        // When: walking through full lifecycle
        processor.ready();                // IDLE → READY
        processor.pause();                // READY → PAUSED
        processor.resume();               // PAUSED → READY
        processor.close();                // READY → STOPPED

        // Then: all transitions recorded in order
        assertThat(transitions).containsExactly(
                ProcessorState.READY,
                ProcessorState.PAUSED,
                ProcessorState.READY,
                ProcessorState.STOPPED
        );
    }

    @Test
    void testFailAndRecover() {
        // Given: a processor in READY state
        var processor = new StatefulProcessor();
        processor.ready();

        // When: failing and recovering
        processor.fail();
        processor.reconnect();
        processor.ready();

        // Then: state is READY
        assertThat(processor.getState()).isEqualTo(ProcessorState.READY);
    }

    @Test
    void testInvalidTransitionThrows() {
        // Given: a processor in IDLE state
        var processor = new StatefulProcessor();

        // When/Then: trying to pause from IDLE throws
        assertThatThrownBy(processor::pause)
                .isInstanceOf(StateTransitionException.class);
    }

    @Test
    void testStoppedIsTerminal() {
        // Given: a stopped processor
        var processor = new StatefulProcessor();
        processor.close();

        // When/Then: trying to transition from STOPPED throws
        assertThatThrownBy(processor::ready)
                .isInstanceOf(StateTransitionException.class);
    }
}
