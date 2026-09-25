package ssg.legoflow.messaging.mqtt.codec;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

class InMemoryMqttTransportTest {

    private InMemoryMqttTransport[] pair;

    @BeforeEach
    void setUp() {
        pair = InMemoryMqttTransport.createPair();
    }

    @Test
    void testSendAndReceive() {
        var data = ByteBuffer.wrap("Hello".getBytes());
        pair[0].send(data);

        var buf = ByteBuffer.allocate(1024);
        int n = pair[1].receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(5);
        buf.flip();
        var received = new String(buf.array(), 0, n);
        assertThat(received).isEqualTo("Hello");
    }

    @Test
    void testReceiveTimeout() {
        var buf = ByteBuffer.allocate(1024);
        int n = pair[0].receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test
    void testClose() {
        assertThat(pair[0].isOpen()).isTrue();
        pair[0].close();
        assertThat(pair[0].isOpen()).isFalse();
        assertThat(pair[1].isOpen()).isTrue(); // independent
    }

    @Test
    void testReceiveAfterClose() {
        pair[0].close();
        var buf = ByteBuffer.allocate(1024);
        int n = pair[0].receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test
    void testLargeData() {
        var data = ByteBuffer.wrap("X".repeat(10000).getBytes());
        pair[0].send(data);

        var buf = ByteBuffer.allocate(1024);
        int total = 0;
        try {
            while (total < 10000) {
                int n = pair[1].receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
                if (n <= 0) break;
                total += n;
                buf.clear();
            }
        } catch (Exception ignored) {}
        assertThat(total).isEqualTo(10000);
    }

    @Test
    void testReceiveBlocking() {
        Thread.startVirtualThread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            pair[0].send(ByteBuffer.wrap("delayed".getBytes()));
        });

        var buf = ByteBuffer.allocate(1024);
        int n = pair[1].receiveWithTimeout(buf, 2, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(7);
    }

    @Test
    void testSendAfterCloseDoesNotQueue() {
        pair[0].close();
        pair[0].send(ByteBuffer.wrap("after close".getBytes()));

        var buf = ByteBuffer.allocate(1024);
        int n = pair[1].receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }
}
