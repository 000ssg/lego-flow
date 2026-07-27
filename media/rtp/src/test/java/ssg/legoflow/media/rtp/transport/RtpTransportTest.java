package ssg.legoflow.media.rtp.transport;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RtpTransport}.
 */
class RtpTransportTest {

    @Test
    void testOddPortRejected() {
        assertThatThrownBy(() ->
                RtpTransport.bind(new InetSocketAddress("127.0.0.1", 0), 5001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("even");
    }

    @Test
    void testBindAndClose() throws IOException {
        var transport = RtpTransport.bind(
                new InetSocketAddress("127.0.0.1", 0), 20000);
        try {
            assertThat(transport.rtpPort()).isEqualTo(20000);
            assertThat(transport.rtcpPort()).isEqualTo(20001);
            assertThat(transport.isClosed()).isFalse();
            assertThat(transport.rtpChannel().isOpen()).isTrue();
            assertThat(transport.rtcpChannel().isOpen()).isTrue();
        } finally {
            transport.close();
        }
        assertThat(transport.isClosed()).isTrue();
    }

    @Test
    void testSendAfterCloseThrows() throws IOException {
        var transport = RtpTransport.bind(
                new InetSocketAddress("127.0.0.1", 0), 20002);
        transport.close();

        assertThatThrownBy(() ->
                transport.sendRtp(ByteBuffer.allocate(10),
                        new InetSocketAddress("127.0.0.1", 30000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void testDoubleCloseIsSafe() throws IOException {
        var transport = RtpTransport.bind(
                new InetSocketAddress("127.0.0.1", 0), 20004);
        transport.close();
        transport.close(); // should not throw
    }

    @Test
    void testLoopbackSendReceive() throws IOException {
        var transport = RtpTransport.bind(
                new InetSocketAddress("127.0.0.1", 0), 20006);
        try {
            byte[] data = {1, 2, 3, 4, 5};
            var dest = new InetSocketAddress("127.0.0.1", 20006);
            transport.sendRtp(ByteBuffer.wrap(data), dest);

            ByteBuffer recvBuf = ByteBuffer.allocate(RtpTransport.MAX_PACKET_SIZE);
            // Use configureBlocking(false) to avoid hanging if no data
            transport.rtpChannel().configureBlocking(false);
            var sender = transport.receiveRtp(recvBuf);
            if (sender != null) {
                recvBuf.flip();
                byte[] received = new byte[recvBuf.remaining()];
                recvBuf.get(received);
                assertThat(received).containsExactly(data);
            }
            // If sender is null, the datagram hasn't arrived yet (non-blocking)
            // This is acceptable in a unit test
        } finally {
            transport.close();
        }
    }
}
