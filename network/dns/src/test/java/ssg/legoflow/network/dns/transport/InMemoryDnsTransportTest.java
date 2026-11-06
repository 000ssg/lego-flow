package ssg.legoflow.network.dns.transport;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.channel.InMemoryByteTransport.TcpSemantics;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;

class InMemoryDnsTransportTest {

    @Test void testCreatePair() {
        var pair = InMemoryDnsTransport.createPair();
        assertThat(pair).hasSize(2);
        assertThat(pair[0].isOpen()).isTrue();
        assertThat(pair[1].isOpen()).isTrue();
    }

    @Test void testSendReceive() {
        var pair = InMemoryDnsTransport.createPair();
        byte[] data = {1, 2, 3, 4, 5};
        pair[0].send(ByteBuffer.wrap(data));

        ByteBuffer buf = ByteBuffer.allocate(10);
        int n = pair[1].receive(buf);
        buf.flip();
        assertThat(n).isEqualTo(5);
    }

    @Test void testBidirectional() {
        var pair = InMemoryDnsTransport.createPair();
        pair[0].withTcpSemantics(TcpSemantics.NONE);
        pair[1].withTcpSemantics(TcpSemantics.NONE);
        pair[0].send(ByteBuffer.wrap(new byte[]{1, 2}));
        pair[1].send(ByteBuffer.wrap(new byte[]{3, 4}));

        ByteBuffer buf1 = ByteBuffer.allocate(10);
        pair[1].receive(buf1);
        buf1.flip();
        assertThat(buf1.get()).isEqualTo((byte) 1);

        ByteBuffer buf2 = ByteBuffer.allocate(10);
        pair[0].receive(buf2);
        buf2.flip();
        assertThat(buf2.get()).isEqualTo((byte) 3);
    }

    @Test void testCloseStopsReceive() {
        var pair = InMemoryDnsTransport.createPair();
        pair[0].close();
        assertThat(pair[0].isOpen()).isFalse();
        int n = pair[0].receive(ByteBuffer.allocate(10));
        assertThat(n).isEqualTo(-1);
    }
}