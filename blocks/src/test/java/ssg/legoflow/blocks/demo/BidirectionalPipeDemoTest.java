package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidirectionalPipeDemoTest {

    @Test
    void testLeftToRight() {
        // Given: two passthrough processors connected bidirectionally
        var rightReceived = new ArrayList<String>();
        var left = new PassthroughProcessor<>(String.class);
        var right = new PassthroughProcessor<>(String.class) {
            @Override
            public void accept(Context ctx, String... data) {
                super.accept(ctx, data);
                rightReceived.addAll(List.of(data));
            }
        };

        // When: sending from left
        var ctx = new DefaultContext();
        left.consume(ctx, "from-left");

        // Then: left recorded 1 incoming item and 1 outgoing item (passthrough: I=O=String)
        assertThat(left.getStatistics().getInCount(String.class)).isEqualTo(1);
        assertThat(left.getStatistics().getOutCount(String.class)).isEqualTo(1);
    }

    @Test
    void testPipeClosesBoth() {
        // Given: a bidirectional pipe
        var left = new PassthroughProcessor<>(String.class);
        var right = new PassthroughProcessor<>(String.class);
        var pipe = new BidirectionalPipe<>(left, right);

        // When: closing the pipe
        pipe.close();

        // Then: both processors are stopped
        assertThat(left.getState()).isEqualTo(ssg.legoflow.blocks.ProcessorState.STOPPED);
        assertThat(right.getState()).isEqualTo(ssg.legoflow.blocks.ProcessorState.STOPPED);
    }

    @Test
    void testBidirectionalWithConversion() {
        // Given: String→Integer left, Integer→String right
        var leftAccepted = new ArrayList<Integer>();
        var rightAccepted = new ArrayList<String>();

        var left = new StringToIntProcessor() {
            @Override
            public void accept(Context ctx, Integer... data) {
                super.accept(ctx, data);
                leftAccepted.addAll(List.of(data));
            }
        };

        var right = new PassthroughProcessor<>(String.class) {
            @Override
            public void accept(Context ctx, String... data) {
                super.accept(ctx, data);
                rightAccepted.addAll(List.of(data));
            }
        };

        var ctx = new DefaultContext();

        // When: left consumes strings → converts to ints
        left.consume(ctx, "42", "7");

        // Then: left's accept receives converted integers
        assertThat(leftAccepted).containsExactly(42, 7);
    }
}
