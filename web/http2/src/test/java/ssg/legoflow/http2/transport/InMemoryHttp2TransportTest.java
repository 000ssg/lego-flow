package ssg.legoflow.http2.transport;

import org.junit.jupiter.api.*;
import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemoryHttp2Transport}.
 *
 * @since 0.3.0
 */
@DisplayName("InMemoryHttp2Transport")
class InMemoryHttp2TransportTest {

    private InMemoryByteTransport[] pair;
    private InMemoryHttp2Transport client;
    private InMemoryHttp2Transport server;

    @BeforeEach
    void setUp() {
        pair = InMemoryHttp2Transport.createPair();
        client = (InMemoryHttp2Transport) pair[0];
        server = (InMemoryHttp2Transport) pair[1];
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    @Test
    @DisplayName("should create connected pair")
    void shouldCreateConnectedPair() {
        assertNotNull(client);
        assertNotNull(server);
        assertTrue(client.isOpen());
        assertTrue(server.isOpen());
    }

    @Test
    @DisplayName("should send and receive HTTP/2 frames")
    void shouldSendAndReceiveFrames() throws Exception {
        // Simulate HTTP/2 connection preface
        byte[] preface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes();
        client.send(ByteBuffer.wrap(preface));

        ByteBuffer received = ByteBuffer.allocate(preface.length);
        int bytesRead = server.receive(received);

        // receive() returns bytes read, may be less than buffer size
        assertTrue(bytesRead > 0);
        assertTrue(bytesRead <= preface.length);
        received.flip();
        byte[] receivedBytes = new byte[received.remaining()];
        received.get(receivedBytes);
        assertArrayEquals(preface, receivedBytes);
    }

    @Test
    @DisplayName("should handle SETTINGS frames")
    void shouldHandleSettingsFrames() throws Exception {
        // HTTP/2 SETTINGS frame: 00 00 00 04 04 00 00 00 00 (type=SETTINGS, len=0, flags=0)
        byte[] settings = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        client.send(ByteBuffer.wrap(settings));

        ByteBuffer received = ByteBuffer.allocate(9);
        int bytesRead = server.receive(received);

        // receive() returns bytes read, may be less than buffer size
        assertTrue(bytesRead > 0);
        assertTrue(bytesRead <= 9);
    }

    @Test
    @DisplayName("should close and return -1 on receive")
    void shouldCloseAndReturnMinusOne() throws Exception {
        client.close();

        ByteBuffer buf = ByteBuffer.allocate(10);
        assertEquals(-1, server.receive(buf));
    }

    @Test
    @DisplayName("should receive with timeout")
    void shouldReceiveWithTimeout() throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(10);

        // Should timeout when no data available
        int result = client.receiveWithTimeout(buf, 100, TimeUnit.MILLISECONDS);
        assertEquals(-1, result);
    }
}
