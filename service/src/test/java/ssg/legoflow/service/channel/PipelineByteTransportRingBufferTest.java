package ssg.legoflow.service.channel;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link PipelineByteTransport} ring buffer.
 */
class PipelineByteTransportRingBufferTest {

    private PipelineByteTransport transport;
    private DataChannel channel;

    @BeforeEach
    void setUp() {
        channel = new TestDataChannel();
        transport = new PipelineByteTransport(channel);
    }

    @AfterEach
    void tearDown() {
        transport.close();
    }

    @Test
    @DisplayName("Basic send and receive")
    void testBasicSendReceive() {
        byte[] data = "hello".getBytes();
        transport.send(ByteBuffer.wrap(data));
        transport.onRead(channel, transport.getOutboundQueue());

        ByteBuffer received = ByteBuffer.allocate(10);
        int n = transport.receiveWithTimeout(received, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(5);
        received.flip();
        assertThat(new String(received.array(), 0, 5)).isEqualTo("hello");
    }

    @Test
    @DisplayName("No data leakage between connections — each connection has its own ring buffer")
    void testNoDataLeakageBetweenConnections() throws Exception {
        byte[] conn1Data = "connection-1-data".getBytes();
        byte[] conn2Data = "connection-2-data".getBytes();

        // Each connection gets its own transport instance with its own ring buffer
        DataChannel channel1 = new TestDataChannel();
        DataChannel channel2 = new TestDataChannel();
        PipelineByteTransport transport1 = new PipelineByteTransport(channel1);
        PipelineByteTransport transport2 = new PipelineByteTransport(channel2);

        try {
            // Connection 1 sends and receives
            transport1.send(ByteBuffer.wrap(conn1Data));
            transport1.onRead(channel1, transport1.getOutboundQueue());
            ByteBuffer received1 = ByteBuffer.allocate(20);
            int n1 = transport1.receiveWithTimeout(received1, 1, TimeUnit.SECONDS);
            assertThat(n1).isEqualTo(17);
            received1.flip();
            assertThat(new String(received1.array(), 0, 17)).isEqualTo("connection-1-data");

            // Connection 2 sends and receives — must NOT see connection 1's data
            transport2.send(ByteBuffer.wrap(conn2Data));
            transport2.onRead(channel2, transport2.getOutboundQueue());
            ByteBuffer received2 = ByteBuffer.allocate(20);
            int n2 = transport2.receiveWithTimeout(received2, 1, TimeUnit.SECONDS);
            assertThat(n2).isEqualTo(17);
            received2.flip();
            assertThat(new String(received2.array(), 0, 17)).isEqualTo("connection-2-data");
        } finally {
            transport1.close();
            transport2.close();
        }
    }

    @Test
    @DisplayName("Buffer wrap-around handled correctly")
    void testBufferWrapAround() {
        int bufferSize = 65536;
        byte[] fillData = new byte[bufferSize - 100];
        for (int i = 0; i < fillData.length; i++) {
            fillData[i] = (byte) (i % 256);
        }
        transport.send(ByteBuffer.wrap(fillData));
        transport.onRead(channel, transport.getOutboundQueue());

        // Read most of it to create wrapped state
        ByteBuffer partial = ByteBuffer.allocate(bufferSize - 200);
        transport.receiveWithTimeout(partial, 1, TimeUnit.SECONDS);

        // Now send more data - this should trigger compaction
        byte[] newData = "after-compact".getBytes();
        transport.send(ByteBuffer.wrap(newData));
        transport.onRead(channel, transport.getOutboundQueue());

        // Read all remaining data (remaining 100 bytes from first send + 13 from second)
        ByteBuffer remaining = ByteBuffer.allocate(200);
        int n = transport.receiveWithTimeout(remaining, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(113);
        remaining.flip();
        // Skip the 100 bytes we already read, verify the new data
        byte[] result = new byte[n];
        remaining.get(result);
        assertThat(new String(result, 100, 13)).isEqualTo("after-compact");
    }

    @Test
    @DisplayName("Multiple sends then single receive")
    void testMultipleSendsSingleReceive() {
        transport.send(ByteBuffer.wrap("part1".getBytes()));
        transport.onRead(channel, transport.getOutboundQueue());
        transport.send(ByteBuffer.wrap("part2".getBytes()));
        transport.onRead(channel, transport.getOutboundQueue());
        transport.send(ByteBuffer.wrap("part3".getBytes()));
        transport.onRead(channel, transport.getOutboundQueue());

        ByteBuffer received = ByteBuffer.allocate(20);
        int n = transport.receiveWithTimeout(received, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(15);
        received.flip();
        assertThat(new String(received.array(), 0, 15)).isEqualTo("part1part2part3");
    }

    @Test
    @DisplayName("Partial receive followed by more data")
    void testPartialReceiveThenMoreData() {
        transport.send(ByteBuffer.wrap("hello-world".getBytes()));
        transport.onRead(channel, transport.getOutboundQueue());

        // Partial receive
        ByteBuffer partial = ByteBuffer.allocate(5);
        int n1 = transport.receiveWithTimeout(partial, 1, TimeUnit.SECONDS);
        assertThat(n1).isEqualTo(5);
        partial.flip();
        assertThat(new String(partial.array())).isEqualTo("hello");

        // Send more data
        transport.send(ByteBuffer.wrap("-continued".getBytes()));
        transport.onRead(channel, transport.getOutboundQueue());

        // Receive remaining (world + continued = 16)
        ByteBuffer rest = ByteBuffer.allocate(20);
        int n2 = transport.receiveWithTimeout(rest, 1, TimeUnit.SECONDS);
        assertThat(n2).isEqualTo(16);
        rest.flip();
        byte[] result = new byte[n2];
        rest.get(result);
        String received = new String(result);
        assertThat(received).isEqualTo("-world-continued");
    }

    @Test
    @DisplayName("Rapid send/receive cycling")
    void testRapidSendReceiveCycling() throws Exception {
        int iterations = 100;
        for (int i = 0; i < iterations; i++) {
            byte[] data = ("message-" + i).getBytes();
            transport.send(ByteBuffer.wrap(data));
            transport.onRead(channel, transport.getOutboundQueue());

            ByteBuffer received = ByteBuffer.allocate(20);
            int n = transport.receiveWithTimeout(received, 1, TimeUnit.SECONDS);
            assertThat(n).isEqualTo(data.length)
                .as("Iteration " + i + " failed");
            received.flip();
            assertThat(new String(received.array(), 0, n))
                .as("Iteration " + i + " data mismatch")
                .isEqualTo(new String(data));
        }
    }

    @Test
    @DisplayName("Empty send does not corrupt state")
    void testEmptySend() {
        transport.send(ByteBuffer.wrap(new byte[0]));
        transport.onRead(channel, transport.getOutboundQueue());

        ByteBuffer received = ByteBuffer.allocate(10);
        int n = transport.receiveWithTimeout(received, 100, TimeUnit.MILLISECONDS);
        assertThat(n).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Close clears buffer")
    void testCloseClearsBuffer() {
        transport.send(ByteBuffer.wrap("data".getBytes()));
        transport.close();

        ByteBuffer received = ByteBuffer.allocate(10);
        int n = transport.receiveWithTimeout(received, 100, TimeUnit.MILLISECONDS);
        assertThat(n).isLessThanOrEqualTo(0);
    }

    /**
     * Simple test channel that stores data internally.
     */
    private static class TestDataChannel implements DataChannel {
        private final ByteBuffer internalBuffer = ByteBuffer.allocate(65536);
        private volatile boolean open = true;

        public void putData(byte[] data) {
            synchronized (internalBuffer) {
                internalBuffer.put(data);
            }
        }

        @Override
        public int read(ByteBuffer dst) {
            synchronized (internalBuffer) {
                internalBuffer.flip();
                int remaining = internalBuffer.remaining();
                if (remaining == 0) {
                    internalBuffer.compact();
                    return 0;
                }
                int toRead = Math.min(remaining, dst.remaining());
                for (int i = 0; i < toRead; i++) {
                    dst.put(internalBuffer.get());
                }
                return toRead;
            }
        }

        @Override
        public int write(ByteBuffer src) {
            synchronized (internalBuffer) {
                internalBuffer.put(src);
                return src.remaining();
            }
        }

        @Override
        public boolean isOpen() { return open; }

        @Override
        public void close() { open = false; }

        @Override
        public SelectionKey getSelectionKey() { return null; }
    }
}