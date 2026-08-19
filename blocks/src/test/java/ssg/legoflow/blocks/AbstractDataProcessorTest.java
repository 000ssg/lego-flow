package ssg.legoflow.blocks;

import ssg.legoflow.blocks.demo.PassthroughProcessor;
import ssg.legoflow.blocks.demo.StringToIntProcessor;
import ssg.legoflow.blocks.demo.ValidationFilter;
import ssg.legoflow.blocks.exceptions.StateTransitionException;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class AbstractDataProcessorTest {

    private final Context ctx = new DefaultContext();

    @Test
    void testPassthroughConsumeCallsAccept() {
        var accepted = new ArrayList<String>();
        var processor = new PassthroughProcessor<>(String.class) {
            @Override
            public void accept(Context ctx, String... data) {
                super.accept(ctx, data);
                accepted.addAll(List.of(data));
            }
        };

        processor.consume(ctx, "hello", "world");

        assertThat(accepted).containsExactly("hello", "world");
    }

    @Test
    void testSubmitCallsProduce() {
        var produced = new ArrayList<String>();
        var processor = new PassthroughProcessor<>(String.class) {
            @Override
            public void produce(Context ctx, String... data) {
                super.produce(ctx, data);
                produced.addAll(List.of(data));
            }
        };

        processor.submit(ctx, "hello");

        assertThat(produced).containsExactly("hello");
    }

    @Test
    void testStringToIntConversion() {
        var accepted = new ArrayList<Integer>();
        var processor = new StringToIntProcessor() {
            @Override
            public void accept(Context ctx, Integer... data) {
                super.accept(ctx, data);
                accepted.addAll(List.of(data));
            }
        };

        processor.consume(ctx, "42", "7", "100");

        assertThat(accepted).containsExactly(42, 7, 100);
    }

    @Test
    void testStringToIntHandlesInvalid() {
        var accepted = new ArrayList<Integer>();
        var processor = new StringToIntProcessor() {
            @Override
            public void accept(Context ctx, Integer... data) {
                super.accept(ctx, data);
                accepted.addAll(List.of(data));
            }
        };

        processor.consume(ctx, "42", "invalid", "7");

        assertThat(accepted).containsExactly(42, 7);
    }

    @Test
    void testInputFilterAppliedOnConsume() {
        var accepted = new ArrayList<String>();
        var processor = new PassthroughProcessor<>(String.class) {
            @Override
            public void accept(Context ctx, String... data) {
                super.accept(ctx, data);
                accepted.addAll(List.of(data));
            }
        };
        processor.addInputFilter(new ValidationFilter<>(String.class, s -> s.length() > 2));

        processor.consume(ctx, "hi", "hello", "yo", "world");

        assertThat(accepted).containsExactly("hello", "world");
    }

    @Test
    void testOutputFilterAppliedOnConsume() {
        var accepted = new ArrayList<Integer>();
        var processor = new StringToIntProcessor() {
            @Override
            public void accept(Context ctx, Integer... data) {
                super.accept(ctx, data);
                accepted.addAll(List.of(data));
            }
        };
        processor.addOutputFilter(new ValidationFilter<>(Integer.class, i -> i > 10));

        processor.consume(ctx, "5", "42", "3", "100");

        assertThat(accepted).containsExactly(42, 100);
    }

    @Test
    void testStatisticsTracking() {
        var processor = new PassthroughProcessor<>(String.class);

        processor.consume(ctx, "a", "b", "c");

        // consume records 3 input items (String) and 3 output items (String, since passthrough)
        assertThat(processor.getStatistics().getInCount(String.class)).isEqualTo(3);
        assertThat(processor.getStatistics().getOutCount(String.class)).isEqualTo(3);
    }

    @Test
    void testInitialStateIsIdle() {
        var processor = new PassthroughProcessor<>(String.class);

        assertThat(processor.getState()).isEqualTo(ProcessorState.IDLE);
    }

    @Test
    void testCloseSetsStopped() {
        var processor = new PassthroughProcessor<>(String.class);

        processor.close();

        assertThat(processor.getState()).isEqualTo(ProcessorState.STOPPED);
    }

    @Test
    void testStateListenerNotifiedOnClose() {
        var processor = new PassthroughProcessor<>(String.class);
        var transitions = new ArrayList<ProcessorState>();
        processor.addStateListener((old, newState) -> transitions.add(newState));

        processor.close();

        assertThat(transitions).containsExactly(ProcessorState.STOPPED);
    }

    @Test
    void testRemoveStateListener() {
        var processor = new PassthroughProcessor<>(String.class);
        var transitions = new ArrayList<ProcessorState>();
        StateListener listener = (old, newState) -> transitions.add(newState);
        processor.addStateListener(listener);
        processor.removeStateListener(listener);

        processor.close();

        assertThat(transitions).isEmpty();
    }

    @Test
    void testCloseIsIdempotent() {
        var processor = new PassthroughProcessor<>(String.class);
        var transitions = new ArrayList<ProcessorState>();
        processor.addStateListener((old, newState) -> transitions.add(newState));

        processor.close();
        processor.close(); // second close should not fire listener again

        assertThat(transitions).hasSize(1);
        assertThat(processor.getState()).isEqualTo(ProcessorState.STOPPED);
    }

    /** Helper subclass to expose {@code transitionTo} for testing. */
    private static class ExposedPassthroughProcessor<T> extends PassthroughProcessor<T> {
        ExposedPassthroughProcessor(Class<T> type) { super(type); }
        void doTransition(ProcessorState s) { transitionTo(s); }
    }

    @Test
    void testInvalidStateTransitionThrows() {
        var processor = new ExposedPassthroughProcessor<>(String.class);

        // IDLE cannot go to PAUSED
        assertThatThrownBy(() -> processor.doTransition(ProcessorState.PAUSED))
                .isInstanceOf(ssg.legoflow.blocks.exceptions.StateTransitionException.class);
    }

    @Test
    void testStateListenerReceivesOldAndNewState() {
        var processor = new ExposedPassthroughProcessor<>(String.class);
        var oldStates = new ArrayList<ProcessorState>();
        var newStates = new ArrayList<ProcessorState>();
        processor.addStateListener((old, newState) -> {
            oldStates.add(old);
            newStates.add(newState);
        });

        processor.doTransition(ProcessorState.READY);

        assertThat(oldStates).containsExactly(ProcessorState.IDLE);
        assertThat(newStates).containsExactly(ProcessorState.READY);
    }

    @Test
    void testConsumeWithAllFiltersRejecting() {
        // If all items are filtered out, accept should not be called
        var accepted = new ArrayList<String>();
        var processor = new PassthroughProcessor<>(String.class) {
            @Override
            public void accept(Context ctx, String... data) {
                super.accept(ctx, data);
                accepted.addAll(List.of(data));
            }
        };
        // Reject everything
        processor.addInputFilter(new ValidationFilter<>(String.class, s -> false));

        processor.consume(ctx, "a", "b", "c");

        assertThat(accepted).isEmpty();
    }

    @Test
    void testProduceRecordsStats() {
        var processor = new PassthroughProcessor<>(String.class);

        processor.produce(ctx, "x", "y");

        // produce records out for inputType (String)
        assertThat(processor.getStatistics().getOutCount(String.class)).isEqualTo(2);
    }

    @Test
    void testSubmitWithAllOutputFiltersRejecting() {
        var produced = new ArrayList<String>();
        var processor = new PassthroughProcessor<>(String.class) {
            @Override
            public void produce(Context ctx, String... data) {
                super.produce(ctx, data);
                produced.addAll(List.of(data));
            }
        };
        // Reject everything on output side
        processor.addOutputFilter(new ValidationFilter<>(String.class, s -> false));

        processor.submit(ctx, "a");

        assertThat(produced).isEmpty();
    }
}
