package ssg.legoflow.messaging.amqp.transport;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineTransportTest {

    private DataChannel mockChannel() {
        return new DataChannel() {
            @Override public int read(ByteBuffer buf) { return 0; }
            @Override public int write(ByteBuffer buf) { return buf.remaining(); }
            @Override public void close() {}
            @Override public boolean isOpen() { return true; }
            @Override public java.nio.channels.SelectionKey getSelectionKey() { return null; }
        };
    }

    @Test
    void testAddAndFetch() {
        var t = new PipelineTransport(mockChannel());
        var src = ByteBuffer.wrap("hello".getBytes());
        t.add(src);
        assertThat(t.peek()).isEqualTo(5);

        var dst = ByteBuffer.allocate(10);
        int n = t.fetch(dst);
        assertThat(n).isEqualTo(5);
        assertThat(t.peek()).isZero();
        dst.flip();
        assertThat(new String(dst.array(), 0, n)).isEqualTo("hello");
    }

    @Test
    void testFetchEmpty() {
        var t = new PipelineTransport(mockChannel());
        var dst = ByteBuffer.allocate(10);
        assertThat(t.fetch(dst)).isZero();
    }

    @Test
    void testPartialFetch() {
        var t = new PipelineTransport(mockChannel());
        t.add(ByteBuffer.wrap("hello".getBytes()));
        var dst = ByteBuffer.allocate(3);
        int n = t.fetch(dst);
        assertThat(n).isEqualTo(3);
        assertThat(t.peek()).isEqualTo(2);
    }

    @Test
    void testLargeAddWraps() {
        var t = new PipelineTransport(mockChannel());
        var data = new byte[60000]; // fits in 64KB buffer
        for (int i = 0; i < data.length; i++) data[i] = (byte)(i % 256);
        t.add(ByteBuffer.wrap(data));
        assertThat(t.peek()).isEqualTo(60000);

        var dst = ByteBuffer.allocate(60000);
        t.fetch(dst);
        assertThat(t.peek()).isZero();
        dst.flip();
        assertThat(dst.array()).isEqualTo(data);
    }

    @Test
    void testMultipleAdds() {
        var t = new PipelineTransport(mockChannel());
        t.add(ByteBuffer.wrap("abc".getBytes()));
        t.add(ByteBuffer.wrap("def".getBytes()));
        assertThat(t.peek()).isEqualTo(6);

        var dst = ByteBuffer.allocate(10);
        int n = t.fetch(dst);
        assertThat(n).isEqualTo(6);
        assertThat(t.peek()).isZero();
    }

    @Test
    void testReceiveWaitsForData() throws InterruptedException {
        var t = new PipelineTransport(mockChannel());
        Thread reader = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(100);
                t.onRead(null, ByteBuffer.wrap("ok".getBytes()));
            } catch (InterruptedException ignored) {}
        });
        var dst = ByteBuffer.allocate(10);
        int n = t.receive(dst);
        assertThat(n).isEqualTo(2);
        reader.join(2000);
    }

    @Test
    void testOnReadReleasesSemaphore() {
        var t = new PipelineTransport(mockChannel());
        t.onRead(null, ByteBuffer.wrap("data".getBytes()));
        assertThat(t.peek()).isEqualTo(4);
    }
}
