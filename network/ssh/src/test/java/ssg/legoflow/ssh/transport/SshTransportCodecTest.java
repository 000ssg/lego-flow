package ssg.legoflow.ssh.transport;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SshTransportCodec}.
 */
class SshTransportCodecTest {

    @Test
    void testEncodeDecodeRoundTrip() {
        SshTransportCodec codec = new SshTransportCodec();
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        byte[] encoded = codec.encode(payload);
        byte[] decoded = codec.decode(encoded);
        assertThat(decoded).isEqualTo(payload);
    }

    @Test
    void testEncodeDecodeEmptyPayload() {
        SshTransportCodec codec = new SshTransportCodec();
        byte[] payload = new byte[0];
        byte[] encoded = codec.encode(payload);
        byte[] decoded = codec.decode(encoded);
        assertThat(decoded).isEqualTo(payload);
    }

    @Test
    void testEncodeDecodeLargePayload() {
        SshTransportCodec codec = new SshTransportCodec();
        byte[] payload = new byte[1000];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i & 0xFF);
        byte[] encoded = codec.encode(payload);
        byte[] decoded = codec.decode(encoded);
        assertThat(decoded).isEqualTo(payload);
    }

    @Test
    void testSequenceNumberIncrement() {
        SshTransportCodec codec = new SshTransportCodec();
        assertThat(codec.outputSequenceNumber()).isEqualTo(0);
        codec.encode(new byte[]{1});
        assertThat(codec.outputSequenceNumber()).isEqualTo(1);
        codec.encode(new byte[]{2});
        assertThat(codec.outputSequenceNumber()).isEqualTo(2);
    }

    @Test
    void testInputSequenceNumberIncrement() {
        SshTransportCodec codec = new SshTransportCodec();
        byte[] encoded = codec.encode(new byte[]{1});
        assertThat(codec.inputSequenceNumber()).isEqualTo(0);
        codec.decode(encoded);
        assertThat(codec.inputSequenceNumber()).isEqualTo(1);
    }

    @Test
    void testResetSequenceNumbers() {
        SshTransportCodec codec = new SshTransportCodec();
        codec.encode(new byte[]{1});
        codec.resetSequenceNumbers();
        assertThat(codec.outputSequenceNumber()).isEqualTo(0);
        assertThat(codec.inputSequenceNumber()).isEqualTo(0);
    }

    @Test
    void testReadWriteString() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        SshTransportCodec.writeString(buf, "hello world");
        buf.flip();
        String result = SshTransportCodec.readString(buf);
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void testReadWriteEmptyString() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        SshTransportCodec.writeString(buf, "");
        buf.flip();
        String result = SshTransportCodec.readString(buf);
        assertThat(result).isEmpty();
    }

    @Test
    void testReadWriteBinary() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        byte[] data = {10, 20, 30, 40, 50};
        SshTransportCodec.writeBinary(buf, data);
        buf.flip();
        byte[] result = SshTransportCodec.readBinary(buf);
        assertThat(result).isEqualTo(data);
    }

    @Test
    void testReadWriteNameList() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        List<String> names = List.of("aes256-ctr", "aes128-ctr", "hmac-sha2-256");
        SshTransportCodec.writeNameList(buf, names);
        buf.flip();
        List<String> result = SshTransportCodec.readNameList(buf);
        assertThat(result).containsExactlyElementsOf(names);
    }

    @Test
    void testReadWriteEmptyNameList() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        SshTransportCodec.writeNameList(buf, List.of());
        buf.flip();
        List<String> result = SshTransportCodec.readNameList(buf);
        assertThat(result).isEmpty();
    }

    @Test
    void testReadWriteBoolean() {
        ByteBuffer buf = ByteBuffer.allocate(2);
        SshTransportCodec.writeBoolean(buf, true);
        SshTransportCodec.writeBoolean(buf, false);
        buf.flip();
        assertThat(SshTransportCodec.readBoolean(buf)).isTrue();
        assertThat(SshTransportCodec.readBoolean(buf)).isFalse();
    }

    @Test
    void testReadWriteUint32() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        SshTransportCodec.writeUint32(buf, 0xFFFFFFFFL);
        SshTransportCodec.writeUint32(buf, 12345L);
        buf.flip();
        assertThat(SshTransportCodec.readUint32(buf)).isEqualTo(0xFFFFFFFFL);
        assertThat(SshTransportCodec.readUint32(buf)).isEqualTo(12345L);
    }

    @Test
    void testPacketPaddingAlignment() {
        SshTransportCodec codec = new SshTransportCodec();
        // Encoded packet size should be aligned to 8 bytes (minimum block)
        byte[] encoded = codec.encode(new byte[]{1, 2, 3});
        // Total packet should be a multiple of 8 (including 4-byte length field)
        int packetLength = ByteBuffer.wrap(encoded).getInt();
        assertThat((packetLength + 4) % 8).isEqualTo(0);
    }

    @Test
    void testMaxPacketSizeConstant() {
        assertThat(SshTransportCodec.MAX_PACKET_SIZE).isEqualTo(35000);
    }

    @Test
    void testMultipleEncodeDecodeRoundTrips() {
        SshTransportCodec codec = new SshTransportCodec();
        for (int i = 0; i < 10; i++) {
            byte[] payload = ("message " + i).getBytes();
            byte[] encoded = codec.encode(payload);
            byte[] decoded = codec.decode(encoded);
            assertThat(decoded).isEqualTo(payload);
        }
        assertThat(codec.outputSequenceNumber()).isEqualTo(10);
    }
}
