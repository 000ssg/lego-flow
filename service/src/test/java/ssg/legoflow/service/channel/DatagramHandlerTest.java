package ssg.legoflow.service.channel;

import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
class DatagramHandlerTest {

    @Test
    void testOnDatagram() {
        AtomicBoolean called = new AtomicBoolean(false);
        AtomicReference<DatagramPacketInfo> packetRef = new AtomicReference<>();
        
        DatagramHandler handler = new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel ch, DatagramPacketInfo pkt) {
                called.set(true);
                packetRef.set(pkt);
            }
            @Override
            public void onSendComplete(DataChannel ch, SocketAddress target) {}
        };
        
        DatagramPacketInfo info = new DatagramPacketInfo(
                new InetSocketAddress("127.0.0.1", 5000),
                ByteBuffer.wrap(new byte[]{1, 2}), System.nanoTime());
        
        handler.onDatagram(null, info);
        assertThat(called.get()).isTrue();
        assertThat(packetRef.get()).isSameAs(info);
    }

    @Test
    void testOnSendComplete() {
        AtomicReference<SocketAddress> targetRef = new AtomicReference<>();
        
        DatagramHandler handler = new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel ch, DatagramPacketInfo pkt) {}
            @Override
            public void onSendComplete(DataChannel ch, SocketAddress target) {
                targetRef.set(target);
            }
        };
        
        SocketAddress addr = new InetSocketAddress("10.0.0.1", 8080);
        handler.onSendComplete(null, addr);
        assertThat(targetRef.get()).isEqualTo(addr);
    }

    @Test
    void testDefaultOnWriteIsNoop() {
        DatagramHandler h = createBaseHandler();
        h.onWrite(null);
    }

    @Test
    void testDefaultOnConnectIsNoop() {
        DatagramHandler h = createBaseHandler();
        h.onConnect(null);
    }

    @Test
    void testDefaultOnDisconnectIsNoop() {
        DatagramHandler h = createBaseHandler();
        h.onDisconnect(null);
    }

    @Test
    void testDefaultOnErrorIsNoop() {
        DatagramHandler h = createBaseHandler();
        h.onError(null, new RuntimeException("test"));
    }

    private DatagramHandler createBaseHandler() {
        return new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel ch, DatagramPacketInfo pkt) {}
            @Override
            public void onSendComplete(DataChannel ch, SocketAddress target) {}
        };
    }

    @Test
    void testDatagramPacketInfoConstructor() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{1, 2, 3});
        SocketAddress addr = new InetSocketAddress("127.0.0.1", 80);
        
        DatagramPacketInfo info = new DatagramPacketInfo(addr, buf, System.nanoTime());
        assertThat(info.sender()).isEqualTo(addr);
        assertThat(info.data()).isNotNull();
    }

    @Test
    void testDatagramPacketInfoSize() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{1, 2, 3, 4});
        DatagramPacketInfo info = new DatagramPacketInfo(
                new InetSocketAddress("127.0.0.1", 80), buf, System.nanoTime());
        
        assertThat(info.size()).isEqualTo(4);
    }

    @Test
    void testDatagramPacketInfoToByteArray() {
        byte[] original = {1, 2, 3};
        DatagramPacketInfo info = new DatagramPacketInfo(
                new InetSocketAddress("127.0.0.1", 80), 
                ByteBuffer.wrap(original), System.nanoTime());
        
        byte[] copy = info.toByteArray();
        assertThat(copy).containsExactly(1, 2, 3);
    }

    @Test
    void testDatagramPacketInfoNullSenderThrows() {
        assertThatThrownBy(() -> new DatagramPacketInfo(null, ByteBuffer.wrap(new byte[]{0}), 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sender");
    }

    @Test
    void testDatagramPacketInfoNullDataThrows() {
        assertThatThrownBy(() -> new DatagramPacketInfo(
                new InetSocketAddress("127.0.0.1", 80), null, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("data");
    }

    @Test
    void testDatagramPacketInfoTimestampPreserved() {
        long timestamp = System.nanoTime();
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{42});
        
        DatagramPacketInfo info = new DatagramPacketInfo(
                new InetSocketAddress("127.0.0.1", 9999), buf, timestamp);
        
        assertThat(info.timestamp()).isEqualTo(timestamp);
    }

    @Test
    void testDatagramPacketInfoDataIsReadOnly() {
        ByteBuffer writable = ByteBuffer.allocate(10);
        writable.put(new byte[]{1, 2, 3});
        writable.flip();
        
        DatagramPacketInfo info = new DatagramPacketInfo(
                new InetSocketAddress("127.0.0.1", 80), writable, System.nanoTime());
        
        assertThatThrownBy(() -> info.data().put((byte)99))
                .isInstanceOf(java.nio.ReadOnlyBufferException.class);
    }

    @Test
    void testDatagramPacketInfoEmptyData() {
        ByteBuffer empty = ByteBuffer.wrap(new byte[0]);
        DatagramPacketInfo info = new DatagramPacketInfo(
                new InetSocketAddress("127.0.0.1", 80), empty, System.nanoTime());
        
        assertThat(info.size()).isZero();
        assertThat(info.toByteArray()).isEmpty();
    }

    @Test
    void testCustomOnWriteImplementation() {
        AtomicBoolean writeCalled = new AtomicBoolean(false);
        
        DatagramHandler handler = new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel ch, DatagramPacketInfo pkt) {}
            @Override
            public void onSendComplete(DataChannel ch, SocketAddress target) {}
            @Override
            public void onWrite(DataChannel channel) {
                writeCalled.set(true);
            }
        };
        
        handler.onWrite(null);
        assertThat(writeCalled.get()).isTrue();
    }

    @Test
    void testCustomOnErrorImplementation() {
        AtomicReference<Throwable> errorCause = new AtomicReference<>();
        
        DatagramHandler handler = new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel ch, DatagramPacketInfo pkt) {}
            @Override
            public void onSendComplete(DataChannel ch, SocketAddress target) {}
            @Override
            public void onError(DataChannel channel, Throwable cause) {
                errorCause.set(cause);
            }
        };
        
        RuntimeException ex = new RuntimeException("test error");
        handler.onError(null, ex);
        assertThat(errorCause.get()).isSameAs(ex);
    }

    @Test
    void testCustomOnDisconnectImplementation() {
        AtomicBoolean disconnectCalled = new AtomicBoolean(false);
        
        DatagramHandler handler = new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel ch, DatagramPacketInfo pkt) {}
            @Override
            public void onSendComplete(DataChannel ch, SocketAddress target) {}
            @Override
            public void onDisconnect(DataChannel channel) {
                disconnectCalled.set(true);
            }
        };
        
        handler.onDisconnect(null);
        assertThat(disconnectCalled.get()).isTrue();
    }

    @Test
    void testHandlerReceivesPacketData() {
        byte[] data = {10, 20, 30};
        DatagramPacketInfo packet = new DatagramPacketInfo(
                new InetSocketAddress("192.168.1.1", 12345), 
                ByteBuffer.wrap(data), System.nanoTime());
        
        AtomicReference<byte[]> capturedData = new AtomicReference<>();
        DatagramHandler handler = new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel ch, DatagramPacketInfo pkt) {
                capturedData.set(pkt.toByteArray());
            }
            @Override
            public void onSendComplete(DataChannel ch, SocketAddress target) {}
        };
        
        handler.onDatagram(null, packet);
        assertThat(capturedData.get()).containsExactly(10, 20, 30);
    }

    @Test
    void testHandlerReceivesSenderAddress() {
        InetSocketAddress expectedAddr = new InetSocketAddress("10.20.30.40", 9999);
        DatagramPacketInfo packet = new DatagramPacketInfo(
                expectedAddr, ByteBuffer.wrap(new byte[]{1}), System.nanoTime());
        
        AtomicReference<SocketAddress> capturedSender = new AtomicReference<>();
        DatagramHandler handler = new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel ch, DatagramPacketInfo pkt) {
                capturedSender.set(pkt.sender());
            }
            @Override
            public void onSendComplete(DataChannel ch, SocketAddress target) {}
        };
        
        handler.onDatagram(null, packet);
        assertThat(capturedSender.get()).isEqualTo(expectedAddr);
    }
}
