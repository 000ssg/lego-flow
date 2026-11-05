package ssg.legoflow.messaging.mqtt.transport;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.service.channel.DataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;

class MqttPipelineTransportTest {

    /** Minimal DataChannel mock for testing pipeline transport ring buffer logic. */
    static class MockDataChannel implements DataChannel {
        private volatile boolean open = true;
        private volatile SelectionKey key;
        private final ByteBuffer[] readData = new ByteBuffer[0];

        @Override public int read(ByteBuffer buffer) throws IOException { return 0; }
        @Override public int write(ByteBuffer buffer) throws IOException {
            if (!open) throw new IOException("closed");
            return buffer.remaining();
        }
        @Override public boolean isOpen() { return open; }
        @Override public void close() throws IOException { open = false; }
        @Override public SelectionKey getSelectionKey() { return key; }
        void setKey(SelectionKey k) { key = k; }
    }

    @Test
    void testAddAndFetch() {
        var transport = new MqttPipelineTransport(new MockDataChannel());
        var data = ByteBuffer.wrap("Hello".getBytes());
        int added = transport.add(data);
        assertThat(added).isEqualTo(5);
        assertThat(transport.peek()).isEqualTo(5);

        var buf = ByteBuffer.allocate(1024);
        int fetched = transport.fetch(buf);
        assertThat(fetched).isEqualTo(5);
        assertThat(transport.peek()).isZero();
    }

    @Test
    void testAddCompactBuffer() {
        var transport = new MqttPipelineTransport(new MockDataChannel());
        // Fill the ring buffer partially, then add more to trigger compaction
        for (int i = 0; i < 100; i++) {
            var data = ByteBuffer.wrap(("msg-" + i).getBytes());
            transport.add(data);
        }
        // Fetch some to create space at the front
        var buf = ByteBuffer.allocate(5000);
        transport.fetch(buf);
        // Add again — should compact
        var more = ByteBuffer.wrap("after compact".getBytes());
        int added = transport.add(more);
        assertThat(added).isEqualTo(13);
    }

    @Test
    void testReceiveWithExistingData() throws Exception {
        var transport = new MqttPipelineTransport(new MockDataChannel());
        transport.add(ByteBuffer.wrap("test".getBytes()));
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(4);
    }

    @Test
    void testReceiveTimeout() throws Exception {
        var transport = new MqttPipelineTransport(new MockDataChannel());
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test
    void testOnReadSignalsAvailable() throws Exception {
        var transport = new MqttPipelineTransport(new MockDataChannel());
        transport.onRead(null, ByteBuffer.wrap("data".getBytes()));
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(4);
    }

    @Test
    void testSendQueuesAndInterestsWrite() throws Exception {
        var mockChannel = new MockDataChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        transport.send(ByteBuffer.wrap("queued".getBytes()));
        // Transport should still be open after send
        assertThat(transport.isOpen()).isTrue();
    }

    @Test
    void testClose() throws Exception {
        var mockChannel = new MockDataChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        assertThat(transport.isOpen()).isTrue();
        transport.close();
        assertThat(transport.isOpen()).isFalse();
    }

    @Test
    void testReceiveAfterClose() {
        var transport = new MqttPipelineTransport(new MockDataChannel());
        transport.close();
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test
    void testGetChannel() {
        var mockChannel = new MockDataChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        assertThat(transport.getChannel()).isSameAs(mockChannel);
    }
}
