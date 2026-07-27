package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.blocks.ProcessorState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimplePassthroughDemoTest {

    @Test
    void testSimplestPassthrough() {
        // Given: a passthrough processor that forwards data unchanged
        var received = new ArrayList<String>();
        var processor = new PassthroughProcessor<>(String.class) {
            @Override
            public void accept(Context ctx, String... data) {
                super.accept(ctx, data);
                received.addAll(List.of(data));
            }
        };
        var ctx = new DefaultContext();

        // When: consuming data
        processor.consume(ctx, "hello", "world");

        // Then: data arrives unchanged at accept
        assertThat(received).containsExactly("hello", "world");
        assertThat(processor.getState()).isEqualTo(ProcessorState.IDLE);
    }

    @Test
    void testPassthroughSubmitToProduce() {
        // Given: a passthrough processor
        var produced = new ArrayList<String>();
        var processor = new PassthroughProcessor<>(String.class) {
            @Override
            public void produce(Context ctx, String... data) {
                super.produce(ctx, data);
                produced.addAll(List.of(data));
            }
        };
        var ctx = new DefaultContext();

        // When: submitting output data
        processor.submit(ctx, "outgoing");

        // Then: data reaches produce unchanged
        assertThat(produced).containsExactly("outgoing");
    }
}
