package ssg.legoflow.media.rtsp.transport;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;

class InMemoryRtspTransportTest {

    @Test void testCreatePair() {
        var pair = InMemoryRtspTransport.createPair();
        assertThat(pair).hasSize(2);
        assertThat(pair[0].isOpen()).isTrue();
    }

    @Test void testSendReceive() {
        var pair = InMemoryRtspTransport.createPair();
        byte[] data = {1, 2, 3, 4, 5};
        pair[0].send(ByteBuffer.wrap(data));

        ByteBuffer buf = ByteBuffer.allocate(10);
        int n = pair[1].receive(buf);
        buf.flip();
        assertThat(n).isEqualTo(5);
    }
}