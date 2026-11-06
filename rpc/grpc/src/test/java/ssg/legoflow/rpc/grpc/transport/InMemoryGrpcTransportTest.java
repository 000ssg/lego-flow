package ssg.legoflow.rpc.grpc.transport;

import org.junit.jupiter.api.*;
import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemoryGrpcTransport}.
 *
 * @since 0.3.0
 */
@DisplayName("InMemoryGrpcTransport")
class InMemoryGrpcTransportTest {

    private InMemoryByteTransport[] pair;
    private InMemoryGrpcTransport client;
    private InMemoryGrpcTransport server;

    @BeforeEach
    void setUp() {
        pair = InMemoryGrpcTransport.createPair();
        client = (InMemoryGrpcTransport) pair[0];
        server = (InMemoryGrpcTransport) pair[1];
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
    @DisplayName("should send and receive gRPC frames")
    void shouldSendAndReceiveGrpcFrames() throws Exception {
        // Simulate gRPC frame: compressed (0) + length (4 bytes) + payload
        byte[] frame = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f};
        client.send(ByteBuffer.wrap(frame));

        ByteBuffer received = ByteBuffer.allocate(10);
        int bytesRead = server.receive(received);

        // receive() returns bytes read, may be less than buffer size
        assertTrue(bytesRead > 0);
        assertTrue(bytesRead <= 10);
        received.flip();
        byte[] receivedBytes = new byte[received.remaining()];
        received.get(receivedBytes);
        assertArrayEquals(frame, receivedBytes);
    }

    @Test
    @DisplayName("should close and return -1 on receive")
    void shouldCloseAndReturnMinusOne() throws Exception {
        client.close();

        ByteBuffer buf = ByteBuffer.allocate(10);
        assertEquals(-1, server.receive(buf));
    }
}
