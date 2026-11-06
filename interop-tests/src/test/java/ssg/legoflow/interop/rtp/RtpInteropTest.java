package ssg.legoflow.interop.rtp;

import org.junit.jupiter.api.*;
import ssg.legoflow.media.rtp.transport.InMemoryRtpTransport;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability test: Lego Flow RTP transport ↔ InMemory transport.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RtpInteropTest {

    @Test
    void testRtpPacketTransfer() throws Exception {
        InMemoryRtpTransport[] pair = InMemoryRtpTransport.createPair();
        InMemoryRtpTransport client = pair[0];
        InMemoryRtpTransport server = pair[1];

        byte[] packet = {(byte)0x80, (byte)0x60, (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01};
        client.send(java.nio.ByteBuffer.wrap(packet));

        java.nio.ByteBuffer received = java.nio.ByteBuffer.allocate(12);
        int bytesRead = server.receive(received);
        assertThat(bytesRead).isGreaterThan(0);

        client.close();
        server.close();
    }
}
