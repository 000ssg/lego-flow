package ssg.legoflow.service.channel;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/**
 * Proves that send() does not mutate the caller's buffer position.
 */
class InMemoryByteTransportBufferTest {

    @Test
    void testSendDoesNotMutateCallerBufferWithNoneSemantics() {
        var pair = InMemoryByteTransport.createPair();
        pair[0].withTcpSemantics(InMemoryByteTransport.TcpSemantics.NONE);
        pair[1].withTcpSemantics(InMemoryByteTransport.TcpSemantics.NONE);

        byte[] data = {1, 2, 3, 4, 5};
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.position(2); // Start at position 2

        int remainingBefore = buf.remaining();
        pair[0].send(buf);

        // PROOF: caller's buffer position should NOT be advanced
        assertThat(buf.position()).isEqualTo(2);
        assertThat(buf.remaining()).isEqualTo(remainingBefore);
    }

    @Test
    void testSendDoesNotMutateCallerBufferWithDefaultSemantics() {
        var pair = InMemoryByteTransport.createPair();
        // DEFAULT semantics
        pair[0].withTcpSemantics(InMemoryByteTransport.TcpSemantics.DEFAULT);
        pair[1].withTcpSemantics(InMemoryByteTransport.TcpSemantics.DEFAULT);

        byte[] data = {1, 2, 3, 4, 5};
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.position(2); // Start at position 2

        int remainingBefore = buf.remaining();
        pair[0].send(buf);

        // PROOF: caller's buffer position should NOT be advanced
        assertThat(buf.position()).isEqualTo(2);
        assertThat(buf.remaining()).isEqualTo(remainingBefore);
    }

    @Test
    void testMultipleSendsWithSameBuffer() {
        var pair = InMemoryByteTransport.createPair();
        pair[0].withTcpSemantics(InMemoryByteTransport.TcpSemantics.NONE);
        pair[1].withTcpSemantics(InMemoryByteTransport.TcpSemantics.NONE);

        byte[] data = {1, 2, 3, 4, 5};

        // Send the same buffer twice (resetting position each time)
        ByteBuffer sendBuf = ByteBuffer.wrap(data);
        pair[0].send(sendBuf);
        sendBuf.rewind(); // Reset to position 0
        pair[0].send(sendBuf);

        // Receive should get 10 bytes total (5 + 5)
        ByteBuffer recvBuf = ByteBuffer.allocate(16);
        int n1 = pair[1].receiveWithTimeout(recvBuf, 1, TimeUnit.SECONDS);
        int n2 = pair[1].receiveWithTimeout(recvBuf, 1, TimeUnit.SECONDS);

        assertThat(n1 + n2).isEqualTo(10);
    }

    @Test
    void testPartialReadDoesNotLoseData() {
        var pair = InMemoryByteTransport.createPair();
        pair[0].withTcpSemantics(InMemoryByteTransport.TcpSemantics.NONE);
        pair[1].withTcpSemantics(InMemoryByteTransport.TcpSemantics.NONE);

        byte[] data = {1, 2, 3, 4, 5};
        pair[0].send(ByteBuffer.wrap(data));

        // First read: 3 bytes
        ByteBuffer buf1 = ByteBuffer.allocate(3);
        int n1 = pair[1].receiveWithTimeout(buf1, 1, TimeUnit.SECONDS);
        assertThat(n1).isEqualTo(3);

        // Second read: should get remaining 2 bytes
        ByteBuffer buf2 = ByteBuffer.allocate(3);
        int n2 = pair[1].receiveWithTimeout(buf2, 1, TimeUnit.SECONDS);
        assertThat(n2).isEqualTo(2);
    }
}
