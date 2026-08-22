package ssg.legoflow.messaging.amqp.transport;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link InMemoryTransport}.
 */
class InMemoryTransportTest {

    @Test void testCreatePair() {
        var pair = InMemoryTransport.createPair();
        assertThat(pair).hasSize(2);
        assertThat(pair[0].isOpen()).isTrue();
        assertThat(pair[1].isOpen()).isTrue();
    }

    @Test void testSendReceive() {
        var pair = InMemoryTransport.createPair();
        byte[] data = {1, 2, 3, 4, 5};
        pair[0].send(ByteBuffer.wrap(data));

        ByteBuffer buf = ByteBuffer.allocate(10);
        int n = pair[1].receive(buf);
        buf.flip();
        assertThat(n).isEqualTo(5);
        byte[] received = new byte[n];
        buf.get(received);
        assertThat(received).isEqualTo(data);
    }

    @Test void testBidirectional() {
        var pair = InMemoryTransport.createPair();
        pair[0].send(ByteBuffer.wrap(new byte[]{1, 2}));
        pair[1].send(ByteBuffer.wrap(new byte[]{3, 4}));

        ByteBuffer buf1 = ByteBuffer.allocate(10);
        pair[1].receive(buf1);
        buf1.flip();
        assertThat(buf1.get()).isEqualTo((byte) 1);
        assertThat(buf1.get()).isEqualTo((byte) 2);

        ByteBuffer buf2 = ByteBuffer.allocate(10);
        pair[0].receive(buf2);
        buf2.flip();
        assertThat(buf2.get()).isEqualTo((byte) 3);
        assertThat(buf2.get()).isEqualTo((byte) 4);
    }

    @Test void testCloseStopsReceive() {
        var pair = InMemoryTransport.createPair();
        pair[0].close();
        assertThat(pair[0].isOpen()).isFalse();
        // Close should unblock receive
        int n = pair[0].receive(ByteBuffer.allocate(10));
        assertThat(n).isEqualTo(-1);
    }

    @Test void testSendAfterClose() {
        var pair = InMemoryTransport.createPair();
        pair[0].close();
        // Should not throw
        pair[0].send(ByteBuffer.wrap(new byte[]{1}));
    }

    @Test void testMultipleSends() {
        var pair = InMemoryTransport.createPair();
        pair[0].send(ByteBuffer.wrap(new byte[]{1}));
        pair[0].send(ByteBuffer.wrap(new byte[]{2}));
        pair[0].send(ByteBuffer.wrap(new byte[]{3}));

        ByteBuffer buf = ByteBuffer.allocate(1);

        pair[1].receive(buf);
        buf.flip();
        assertThat(buf.get()).isEqualTo((byte) 1);

        buf.clear();
        pair[1].receive(buf);
        buf.flip();
        assertThat(buf.get()).isEqualTo((byte) 2);

        buf.clear();
        pair[1].receive(buf);
        buf.flip();
        assertThat(buf.get()).isEqualTo((byte) 3);
    }
}
