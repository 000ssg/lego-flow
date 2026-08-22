package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link StompTransaction}.
 *
 * @since 0.1.0
 */
class StompTransactionTest {

    @Test
    void testCreateTransaction() {
        var tx = new StompTransaction("tx-1");
        assertThat(tx.getTransactionId()).isEqualTo("tx-1");
        assertThat(tx.isActive()).isTrue();
        assertThat(tx.isCommitted()).isFalse();
        assertThat(tx.isAborted()).isFalse();
        assertThat(tx.size()).isZero();
    }

    @Test
    void testBufferFrames() {
        var tx = new StompTransaction("tx-1");
        var frame1 = new StompFrame(StompCommand.SEND);
        var frame2 = new StompFrame(StompCommand.ACK);
        tx.buffer(frame1);
        tx.buffer(frame2);
        assertThat(tx.size()).isEqualTo(2);
        assertThat(tx.getBufferedFrames()).containsExactly(frame1, frame2);
    }

    @Test
    void testCommit() {
        var tx = new StompTransaction("tx-1");
        var frame = new StompFrame(StompCommand.SEND);
        tx.buffer(frame);

        var committed = tx.commit();
        assertThat(committed).containsExactly(frame);
        assertThat(tx.isCommitted()).isTrue();
        assertThat(tx.isActive()).isFalse();
    }

    @Test
    void testAbort() {
        var tx = new StompTransaction("tx-1");
        tx.buffer(new StompFrame(StompCommand.SEND));
        tx.buffer(new StompFrame(StompCommand.SEND));

        tx.abort();
        assertThat(tx.isAborted()).isTrue();
        assertThat(tx.isActive()).isFalse();
        assertThat(tx.size()).isZero();
    }

    @Test
    void testBufferAfterCommitFails() {
        var tx = new StompTransaction("tx-1");
        tx.commit();

        assertThatThrownBy(() -> tx.buffer(new StompFrame(StompCommand.SEND)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("committed");
    }

    @Test
    void testBufferAfterAbortFails() {
        var tx = new StompTransaction("tx-1");
        tx.abort();

        assertThatThrownBy(() -> tx.buffer(new StompFrame(StompCommand.SEND)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aborted");
    }

    @Test
    void testDoubleCommitFails() {
        var tx = new StompTransaction("tx-1");
        tx.commit();

        assertThatThrownBy(tx::commit)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testCommitAfterAbortFails() {
        var tx = new StompTransaction("tx-1");
        tx.abort();

        assertThatThrownBy(tx::commit)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testToString() {
        var tx = new StompTransaction("tx-1");
        tx.buffer(new StompFrame(StompCommand.SEND));
        assertThat(tx.toString()).contains("tx-1").contains("1").contains("active");
    }
}
