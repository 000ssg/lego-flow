package ssg.legoflow.messaging.stomp.transport;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

class InMemoryTransportTest {

    @Test
    void testPairSendAndReceive() throws Exception {
        var pair = InMemoryTransport.createPair();
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
    void testReceiveTimeout() throws Exception {
        var pair = InMemoryTransport.createPair();
        var buf = ByteBuffer.allocate(1024);
        int n = pair[0].receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test
    void testClose() {
        var pair = InMemoryTransport.createPair();
        assertThat(pair[0].isOpen()).isTrue();
        pair[0].close();
        assertThat(pair[0].isOpen()).isFalse();
        assertThat(pair[1].isOpen()).isTrue(); // independent
    }

    @Test
    void testSendAfterCloseDoesNotQueue() {
        var pair = InMemoryTransport.createPair();
        pair[0].close();
        var data = ByteBuffer.wrap("after close".getBytes());
        pair[0].send(data); // should not throw, just not queue

        var buf = ByteBuffer.allocate(1024);
        int n = pair[1].receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test
    void testReceiveDataThenClose() throws Exception {
        var pair = InMemoryTransport.createPair();
        var data = ByteBuffer.wrap("before close".getBytes());
        pair[0].send(data);
        pair[0].close();

        // Peer can still read the queued message
        var buf = ByteBuffer.allocate(1024);
        int n = pair[1].receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(12); // "before close"
    }

    @Test
    void testReceiveWithSmallBuffer() throws Exception {
        var pair = InMemoryTransport.createPair();
        var data = ByteBuffer.wrap("Hello World".getBytes());
        pair[0].send(data);

        var buf = ByteBuffer.allocate(5);
        int n = pair[1].receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(5); // capped to buffer size
    }

    @Test
    void testMultipleMessages() throws Exception {
        var pair = InMemoryTransport.createPair();
        pair[0].send(ByteBuffer.wrap("first".getBytes()));
        pair[0].send(ByteBuffer.wrap("second".getBytes()));

        var buf = ByteBuffer.allocate(1024);
        int n1 = pair[1].receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n1).isEqualTo(5); // "first"
        buf.clear();
        int n2 = pair[1].receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n2).isEqualTo(6); // "second"
    }

    @Test
    void testReceiveBlocking() throws Exception {
        var pair = InMemoryTransport.createPair();
        // Message arrives after a short delay
        Thread.startVirtualThread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            pair[0].send(ByteBuffer.wrap("delayed".getBytes()));
        });

        var buf = ByteBuffer.allocate(1024);
        int n = pair[1].receiveWithTimeout(buf, 2, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(7);
    }
}
