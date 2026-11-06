package ssg.legoflow.interop.grpc;

import org.junit.jupiter.api.*;
import ssg.legoflow.rpc.grpc.transport.InMemoryGrpcTransport;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability test: Lego Flow gRPC transport ↔ InMemory transport.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrpcInteropTest {

    @Test
    void testGrpcFrameTransfer() throws Exception {
        InMemoryGrpcTransport[] pair = InMemoryGrpcTransport.createPair();
        InMemoryGrpcTransport client = pair[0];
        InMemoryGrpcTransport server = pair[1];

        byte[] frame = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f};
        client.send(java.nio.ByteBuffer.wrap(frame));

        java.nio.ByteBuffer received = java.nio.ByteBuffer.allocate(10);
        int bytesRead = server.receive(received);
        assertThat(bytesRead).isGreaterThan(0);

        client.close();
        server.close();
    }
}
