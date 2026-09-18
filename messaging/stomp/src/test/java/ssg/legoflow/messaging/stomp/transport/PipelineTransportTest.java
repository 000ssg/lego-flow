package ssg.legoflow.messaging.stomp.transport;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.service.channel.DataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;

class PipelineTransportTest {

    static class MockChannel implements DataChannel {
        private volatile boolean open = true;
        private volatile SelectionKey key;
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

    @Test void testAddAndFetch() {
        var transport = new PipelineTransport(new MockChannel());
        var data = ByteBuffer.wrap("Hello".getBytes());
        int added = transport.add(data);
        assertThat(added).isEqualTo(5);
        assertThat(transport.peek()).isEqualTo(5);

        var buf = ByteBuffer.allocate(1024);
        int fetched = transport.fetch(buf);
        assertThat(fetched).isEqualTo(5);
        assertThat(transport.peek()).isZero();
    }

    @Test void testOnRead() throws Exception {
        var transport = new PipelineTransport(new MockChannel());
        transport.onRead(null, ByteBuffer.wrap("data".getBytes()));
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(4);
    }

    @Test void testReceiveTimeout() {
        var transport = new PipelineTransport(new MockChannel());
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test void testSend() {
        var transport = new PipelineTransport(new MockChannel());
        transport.send(ByteBuffer.wrap("test".getBytes()));
        assertThat(transport.isOpen()).isTrue();
    }

    @Test void testClose() throws Exception {
        var mockChannel = new MockChannel();
        var transport = new PipelineTransport(mockChannel);
        transport.close();
        assertThat(transport.isOpen()).isFalse();
    }

    @Test void testReceiveAfterClose() {
        var transport = new PipelineTransport(new MockChannel());
        transport.close();
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test void testGetChannel() {
        var mockChannel = new MockChannel();
        var transport = new PipelineTransport(mockChannel);
        assertThat(transport.getChannel()).isSameAs(mockChannel);
    }

    @Test void testLargeData() {
        var transport = new PipelineTransport(new MockChannel());
        var data = "X".repeat(10000).getBytes();
        int added = transport.add(ByteBuffer.wrap(data));
        assertThat(added).isEqualTo(10000);

        var buf = ByteBuffer.allocate(10000);
        int fetched = transport.fetch(buf);
        assertThat(fetched).isEqualTo(10000);
    }
}
