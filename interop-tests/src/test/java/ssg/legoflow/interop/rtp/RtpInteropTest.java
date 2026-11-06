package ssg.legoflow.interop.rtp;

import org.junit.jupiter.api.*;
import ssg.legoflow.media.rtp.RtpByteTransport;
import ssg.legoflow.service.channel.InMemoryByteTransport;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability test: Lego Flow RTP transport ↔ InMemory transport.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RtpInteropTest {

    @Test
    void testRtpPacketTransfer() throws Exception {
        InMemoryByteTransport[] pair = RtpByteTransport.createPair();
        RtpByteTransport client = (RtpByteTransport) pair[0];
        RtpByteTransport server = (RtpByteTransport) pair[1];

        byte[] packet = {0x80, 0x60, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01};
        client.send(java.nio.ByteBuffer.wrap(packet));

        java.nio.ByteBuffer received = java.nio.ByteBuffer.allocate(12);
        int bytesRead = server.receive(received);
        assertThat(bytesRead).isGreaterThan(0);

        client.close();
        server.close();
    }
}
