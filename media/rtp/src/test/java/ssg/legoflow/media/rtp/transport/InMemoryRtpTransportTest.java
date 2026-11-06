package ssg.legoflow.media.rtp.transport;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;

class InMemoryRtpTransportTest {

    @Test void testCreatePair() {
        var pair = InMemoryRtpTransport.createPair();
        assertThat(pair).hasSize(2);
        assertThat(pair[0].isOpen()).isTrue();
    }

    @Test void testSendReceive() {
        var pair = InMemoryRtpTransport.createPair();
        byte[] data = {1, 2, 3, 4, 5};
        pair[0].send(ByteBuffer.wrap(data));

        ByteBuffer buf = ByteBuffer.allocate(10);
        int n = pair[1].receive(buf);
        buf.flip();
        assertThat(n).isEqualTo(5);
    }
}