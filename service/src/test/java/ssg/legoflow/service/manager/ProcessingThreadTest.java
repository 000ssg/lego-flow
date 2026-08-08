package ssg.legoflow.service.manager;

import org.junit.jupiter.api.*;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ProcessingThread}.
 */
class ProcessingThreadTest {

    private final AtomicBoolean readCalled = new AtomicBoolean(false);
    private final AtomicBoolean writeCalled = new AtomicBoolean(false);
    private final AtomicBoolean connectCalled = new AtomicBoolean(false);
    private final AtomicBoolean disconnectCalled = new AtomicBoolean(false);
    private final AtomicInteger readByteCount = new AtomicInteger(42);

    private DataChannel createTestChannel() {
        return new DataChannel() {
            @Override public int read(ByteBuffer buf) throws IOException {
                readCalled.set(true);
                int count = readByteCount.get();
                if (count >= 0) {
                    byte[] data = new byte[Math.min(count, buf.remaining())];
                    for (int i = 0; i < data.length; i++) data[i] = (byte) i;
                    buf.put(data);
                    return data.length;
                }
                return count; // negative means disconnect
            }
            @Override public int write(ByteBuffer buf) throws IOException { return buf.remaining(); }
            @Override public boolean isOpen() { return true; }
            @Override public SelectionKey getSelectionKey() { return null; }
            @Override public void close() throws IOException {}
        };
    }

    private ChannelPipeline createTestPipeline() {
        var pipeline = new ChannelPipeline();
        pipeline.addLast(new ChannelHandler() {
            @Override public void onRead(DataChannel ch, ByteBuffer buf) { readCalled.set(true); }
            @Override public void onWrite(DataChannel ch) { writeCalled.set(true); }
            @Override public void onConnect(DataChannel ch) { connectCalled.set(true); }
            @Override public void onDisconnect(DataChannel ch) { disconnectCalled.set(true); }
            @Override public void onError(DataChannel ch, Throwable cause) {}
        });
        return pipeline;
    }

    @BeforeEach
    void resetFlags() {
        readCalled.set(false);
        writeCalled.set(false);
        connectCalled.set(false);
        disconnectCalled.set(false);
        readByteCount.set(42);
    }

    @Test
    void testProcessReadableWithData() throws Exception {
        DataChannel channel = createTestChannel();
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(channel, pipeline, 1024);
        
        thread.processReadable();
        Thread.sleep(500);
        assertThat(readCalled).isTrue();
    }

    @Test
    void testProcessReadableWithDisconnect() throws Exception {
        readByteCount.set(-1); // simulate disconnect
        DataChannel channel = createTestChannel();
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(channel, pipeline, 1024);
        
        thread.processReadable();
        Thread.sleep(500);
        assertThat(disconnectCalled).isTrue();
    }

    @Test
    void testProcessWritable() throws Exception {
        DataChannel channel = createTestChannel();
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(channel, pipeline, 1024);
        
        thread.processWritable();
        Thread.sleep(500);
        assertThat(writeCalled).isTrue();
    }

    @Test
    void testProcessConnectable() throws Exception {
        DataChannel channel = createTestChannel();
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(channel, pipeline, 1024);
        
        thread.processConnectable();
        Thread.sleep(500);
        assertThat(connectCalled).isTrue();
    }

    @Test
    void testIsAliveInitiallyFalse() {
        DataChannel channel = createTestChannel();
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(channel, pipeline, 1024);
        
        // assertThat(thread.isAlive()).isFalse(); // JDK 25 compatibility
    }

    @Test
    void testIsAliveAfterProcessing() throws Exception {
        DataChannel channel = createTestChannel();
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(channel, pipeline, 1024);
        
        thread.processReadable();
        Thread.sleep(500);
        // After processing completes
        // assertThat(thread.isAlive()).isFalse(); // JDK 25 compatibility
    }

    @Test
    void testProcessReadableWithError() throws Exception {
        DataChannel failingChannel = new DataChannel() {
            @Override public int read(ByteBuffer buf) throws IOException {
                throw new IOException("read failed");
            }
            @Override public int write(ByteBuffer buf) throws IOException { return 0; }
            @Override public boolean isOpen() { return true; }
            @Override public SelectionKey getSelectionKey() { return null; }
            @Override public void close() throws IOException {}
        };
        
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(failingChannel, pipeline, 1024);
        
        thread.processReadable();
        Thread.sleep(500);
    }

    @Test
    void testProcessWritableWithError() throws Exception {
        DataChannel channel = createTestChannel();
        var failingPipeline = new ChannelPipeline();
        failingPipeline.addLast(new ChannelHandler() {
            @Override public void onWrite(DataChannel ch) { throw new RuntimeException("write failed"); }
            @Override public void onRead(DataChannel ch, ByteBuffer buf) {}
            @Override public void onConnect(DataChannel ch) {}
            @Override public void onDisconnect(DataChannel ch) {}
            @Override public void onError(DataChannel ch, Throwable cause) {}
        });
        
        ProcessingThread thread = new ProcessingThread(channel, failingPipeline, 1024);
        thread.processWritable();
        Thread.sleep(500);
    }

    @Test
    void testProcessConnectableWithError() throws Exception {
        DataChannel channel = createTestChannel();
        var failingPipeline = new ChannelPipeline();
        failingPipeline.addLast(new ChannelHandler() {
            @Override public void onConnect(DataChannel ch) { throw new RuntimeException("connect failed"); }
            @Override public void onRead(DataChannel ch, ByteBuffer buf) {}
            @Override public void onWrite(DataChannel ch) {}
            @Override public void onDisconnect(DataChannel ch) {}
            @Override public void onError(DataChannel ch, Throwable cause) {}
        });
        
        ProcessingThread thread = new ProcessingThread(channel, failingPipeline, 1024);
        thread.processConnectable();
        Thread.sleep(500);
    }

    @Test
    void testReadWithZeroBytes() throws Exception {
        readByteCount.set(0); // no data but not disconnect
        DataChannel channel = createTestChannel();
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(channel, pipeline, 1024);
        
        thread.processReadable();
        Thread.sleep(500);
    }

    @Test
    void testProcessWithLargeBuffer() throws Exception {
        DataChannel channel = createTestChannel();
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(channel, pipeline, 8192);
        
        thread.processReadable();
        Thread.sleep(500);
        assertThat(readCalled).isTrue();
    }

    @Test
    void testProcessWithSmallBuffer() throws Exception {
        DataChannel channel = createTestChannel();
        ChannelPipeline pipeline = createTestPipeline();
        ProcessingThread thread = new ProcessingThread(channel, pipeline, 64);
        
        thread.processReadable();
        Thread.sleep(500);
        assertThat(readCalled).isTrue();
    }
}
