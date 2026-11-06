package ssg.legoflow.http3.transport;

import org.junit.jupiter.api.*;
import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemoryHttp3Transport}.
 *
 * @since 0.3.0
 */
@DisplayName("InMemoryHttp3Transport")
class InMemoryHttp3TransportTest {

    private InMemoryByteTransport[] pair;
    private InMemoryHttp3Transport client;
    private InMemoryHttp3Transport server;

    @BeforeEach
    void setUp() {
        pair = InMemoryHttp3Transport.createPair();
        client = (InMemoryHttp3Transport) pair[0];
        server = (InMemoryHttp3Transport) pair[1];
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
    @DisplayName("should send and receive QUIC packets")
    void shouldSendAndReceiveQuicPackets() throws Exception {
        // Simulate QUIC Initial packet
        byte[] packet = {(byte) 0xc0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01};
        client.send(ByteBuffer.wrap(packet));

        ByteBuffer received = ByteBuffer.allocate(5);
        int bytesRead = server.receive(received);

        // receive() returns bytes read, may be less than buffer size
        assertTrue(bytesRead > 0);
        assertTrue(bytesRead <= 5);
        received.flip();
        byte[] receivedBytes = new byte[received.remaining()];
        received.get(receivedBytes);
        assertArrayEquals(packet, receivedBytes);
    }

    @Test
    @DisplayName("should close and return -1 on receive")
    void shouldCloseAndReturnMinusOne() throws Exception {
        client.close();

        ByteBuffer buf = ByteBuffer.allocate(10);
        assertEquals(-1, server.receive(buf));
    }
}
