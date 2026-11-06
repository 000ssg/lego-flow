package ssg.legoflow.http.transport;

import org.junit.jupiter.api.*;
import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemoryHttpTransport}.
 *
 * @since 0.3.0
 */
@DisplayName("InMemoryHttpTransport")
class InMemoryHttpTransportTest {

    private InMemoryByteTransport[] pair;
    private InMemoryHttpTransport client;
    private InMemoryHttpTransport server;

    @BeforeEach
    void setUp() {
        pair = InMemoryHttpTransport.createPair();
        client = (InMemoryHttpTransport) pair[0];
        server = (InMemoryHttpTransport) pair[1];
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
    @DisplayName("should send and receive bytes")
    void shouldSendAndReceiveBytes() throws Exception {
        byte[] data = "Hello HTTP".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data);

        client.send(buffer);

        ByteBuffer received = ByteBuffer.allocate(12);
        int bytesRead = server.receive(received);

        // receive() returns bytes read, may be less than buffer size
        assertTrue(bytesRead > 0);
        assertTrue(bytesRead <= 12);
        received.flip();
        byte[] receivedBytes = new byte[received.remaining()];
        received.get(receivedBytes);
        assertArrayEquals(data, receivedBytes);
    }

    @Test
    @DisplayName("should handle multiple sends")
    void shouldHandleMultipleSends() throws Exception {
        client.send(ByteBuffer.wrap("A".getBytes()));
        client.send(ByteBuffer.wrap("B".getBytes()));
        client.send(ByteBuffer.wrap("C".getBytes()));

        ByteBuffer buf = ByteBuffer.allocate(3);
        assertEquals(1, server.receive(buf));
        buf.clear();
        assertEquals(1, server.receive(buf));
        buf.clear();
        assertEquals(1, server.receive(buf));
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

    @Test
    @DisplayName("should support large data transfer")
    void shouldSupportLargeDataTransfer() throws Exception {
        byte[] largeData = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        client.send(ByteBuffer.wrap(largeData));

        ByteBuffer received = ByteBuffer.allocate(largeData.length);
        int totalRead = 0;
        int bytesRead;
        while ((bytesRead = server.receive(received)) != -1) {
            totalRead += bytesRead;
            if (totalRead >= largeData.length) {
                break;
            }
        }

        assertEquals(largeData.length, totalRead);
    }
}
