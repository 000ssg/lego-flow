package ssg.legoflow.coap.transport;

import org.junit.jupiter.api.*;
import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemoryCoapTransport}.
 *
 * @since 0.3.0
 */
@DisplayName("InMemoryCoapTransport")
class InMemoryCoapTransportTest {

    private InMemoryByteTransport[] pair;
    private InMemoryCoapTransport client;
    private InMemoryCoapTransport server;

    @BeforeEach
    void setUp() {
        pair = InMemoryCoapTransport.createPair();
        client = (InMemoryCoapTransport) pair[0];
        server = (InMemoryCoapTransport) pair[1];
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
    @DisplayName("should send and receive CoAP messages")
    void shouldSendAndReceiveCoapMessages() throws Exception {
        // Simulate CoAP message: version=1, type=CON, token len=0, code=0.01 (GET), message id=1
        byte[] message = {(byte) 0x41, (byte) 0x01, (byte) 0x00, (byte) 0x01};
        client.send(ByteBuffer.wrap(message));

        ByteBuffer received = ByteBuffer.allocate(4);
        int bytesRead = server.receive(received);

        // receive() returns bytes read, may be less than buffer size
        assertTrue(bytesRead > 0);
        assertTrue(bytesRead <= 4);
        received.flip();
        byte[] receivedBytes = new byte[received.remaining()];
        received.get(receivedBytes);
        assertArrayEquals(message, receivedBytes);
    }

    @Test
    @DisplayName("should close and return -1 on receive")
    void shouldCloseAndReturnMinusOne() throws Exception {
        client.close();

        ByteBuffer buf = ByteBuffer.allocate(10);
        assertEquals(-1, server.receive(buf));
    }
}
