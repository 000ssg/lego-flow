package ssg.legoflow.blocks.exceptions;

import ssg.legoflow.blocks.ProcessorState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for exception classes in the blocks module.
 */
class ExceptionsTest {

    @Test
    void testProcessorExceptionMessage() {
        var ex = new ProcessorException("test message");
        assertThat(ex.getMessage()).isEqualTo("test message");
    }

    @Test
    void testProcessorExceptionMessageAndCause() {
        var cause = new RuntimeException("root cause");
        var ex = new ProcessorException("test message", cause);
        assertThat(ex.getMessage()).isEqualTo("test message");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void testProcessorExceptionIsRuntimeException() {
        var ex = new ProcessorException("msg");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testFilterRejectedExceptionMessage() {
        var ex = new FilterRejectedException("filter rejected item");
        assertThat(ex.getMessage()).isEqualTo("filter rejected item");
    }

    @Test
    void testFilterRejectedExceptionIsProcessorException() {
        var ex = new FilterRejectedException("msg");
        assertThat(ex).isInstanceOf(ProcessorException.class);
    }

    @Test
    void testStateTransitionExceptionMessage() {
        var ex = new StateTransitionException(ProcessorState.IDLE, ProcessorState.PAUSED);
        assertThat(ex.getMessage()).contains("IDLE");
        assertThat(ex.getMessage()).contains("PAUSED");
    }

    @Test
    void testStateTransitionExceptionGetFrom() {
        var ex = new StateTransitionException(ProcessorState.READY, ProcessorState.IDLE);
        assertThat(ex.getFrom()).isEqualTo(ProcessorState.READY);
    }

    @Test
    void testStateTransitionExceptionGetTo() {
        var ex = new StateTransitionException(ProcessorState.READY, ProcessorState.IDLE);
        assertThat(ex.getTo()).isEqualTo(ProcessorState.IDLE);
    }

    @Test
    void testStateTransitionExceptionIsProcessorException() {
        var ex = new StateTransitionException(ProcessorState.IDLE, ProcessorState.PAUSED);
        assertThat(ex).isInstanceOf(ProcessorException.class);
    }
}
