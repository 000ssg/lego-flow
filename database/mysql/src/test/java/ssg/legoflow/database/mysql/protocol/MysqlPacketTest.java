package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link MysqlPacket}.
 */
class MysqlPacketTest {

    @Test
    void testEncode_smallPayload() {
        var packet = new MysqlPacket(0, new byte[]{1, 2, 3});
        var encoded = packet.encode();

        assertThat(encoded).hasSize(7); // 4 header + 3 payload
        assertThat(encoded[0]).isEqualTo((byte) 3); // length low
        assertThat(encoded[1]).isEqualTo((byte) 0); // length mid
        assertThat(encoded[2]).isEqualTo((byte) 0); // length high
        assertThat(encoded[3]).isEqualTo((byte) 0); // sequence id
        assertThat(encoded[4]).isEqualTo((byte) 1);
        assertThat(encoded[5]).isEqualTo((byte) 2);
        assertThat(encoded[6]).isEqualTo((byte) 3);
    }

    @Test
    void testDecode_smallPayload() {
        var data = new byte[]{3, 0, 0, 0, 10, 20, 30};
        var packet = MysqlPacket.decode(data);

        assertThat(packet.sequenceId()).isEqualTo(0);
        assertThat(packet.payload()).containsExactly(10, 20, 30);
    }

    @Test
    void testEncodeDecode_roundTrip() {
        var original = new MysqlPacket(42, new byte[]{5, 6, 7, 8});
        var decoded = MysqlPacket.decode(original.encode());

        assertThat(decoded.sequenceId()).isEqualTo(42);
        assertThat(decoded.payload()).isEqualTo(original.payload());
    }

    @Test
    void testSequenceId_wrapping() {
        var packet = new MysqlPacket(255, new byte[]{1});
        var encoded = packet.encode();
        assertThat(encoded[3] & 0xFF).isEqualTo(255);
    }

    @Test
    void testEmptyPayload() {
        var packet = new MysqlPacket(0, new byte[0]);
        var encoded = packet.encode();
        assertThat(encoded).hasSize(4);
        assertThat(encoded[0]).isEqualTo((byte) 0);
    }

    @Test
    void testReadFrom_stream() throws IOException {
        var packet = new MysqlPacket(1, "hello".getBytes());
        var baos = new ByteArrayOutputStream();
        packet.writeTo(baos);

        var read = MysqlPacket.readFrom(new ByteArrayInputStream(baos.toByteArray()));
        assertThat(read.sequenceId()).isEqualTo(1);
        assertThat(new String(read.payload())).isEqualTo("hello");
    }

    @Test
    void testWriteRead_stream_roundTrip() throws IOException {
        var original = new MysqlPacket(5, new byte[]{100, (byte) 200, 50});
        var baos = new ByteArrayOutputStream();
        original.writeTo(baos);

        var read = MysqlPacket.readFrom(new ByteArrayInputStream(baos.toByteArray()));
        assertThat(read.sequenceId()).isEqualTo(5);
        assertThat(read.payload()).isEqualTo(original.payload());
    }

    @Test
    void testReadFrom_shortHeader_throwsIOException() {
        assertThatThrownBy(() -> MysqlPacket.readFrom(new ByteArrayInputStream(new byte[]{1, 2})))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testLargePayload_split() {
        byte[] largePayload = new byte[MysqlPacket.MAX_PAYLOAD_SIZE + 100];
        var packet = new MysqlPacket(0, largePayload);
        var packets = packet.split();

        assertThat(packets).hasSize(2);
        assertThat(packets.get(0).payload()).hasSize(MysqlPacket.MAX_PAYLOAD_SIZE);
        assertThat(packets.get(1).payload()).hasSize(100);
        assertThat(packets.get(0).sequenceId()).isEqualTo(0);
        assertThat(packets.get(1).sequenceId()).isEqualTo(1);
    }

    @Test
    void testExactMaxPayload_split() {
        byte[] payload = new byte[MysqlPacket.MAX_PAYLOAD_SIZE];
        var packet = new MysqlPacket(0, payload);
        var packets = packet.split();

        // Exact max payload needs terminator empty packet
        assertThat(packets).hasSize(2);
        assertThat(packets.get(1).payload()).hasSize(0);
    }

    @Test
    void testSmallPayload_noSplit() {
        var packet = new MysqlPacket(0, new byte[100]);
        var packets = packet.split();
        assertThat(packets).hasSize(1);
    }

    @Test
    void testReadFullFrom_noSplit() throws IOException {
        var packet = new MysqlPacket(0, "test data".getBytes());
        var baos = new ByteArrayOutputStream();
        packet.writeTo(baos);

        var full = MysqlPacket.readFullFrom(new ByteArrayInputStream(baos.toByteArray()));
        assertThat(new String(full.payload())).isEqualTo("test data");
    }

    @Test
    void testPayloadBuffer() {
        var packet = new MysqlPacket(0, new byte[]{1, 2, 3});
        var buf = packet.payloadBuffer();
        assertThat(buf.remaining()).isEqualTo(3);
        assertThat(buf.get()).isEqualTo((byte) 1);
    }

    @Test
    void testDecode_tooShort_throwsException() {
        assertThatThrownBy(() -> MysqlPacket.decode(new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPayloadLength_encoding() {
        // Test that payload length is properly encoded in little-endian
        var packet = new MysqlPacket(0, new byte[256]);
        var encoded = packet.encode();
        assertThat(encoded[0] & 0xFF).isEqualTo(0); // 256 & 0xFF
        assertThat(encoded[1] & 0xFF).isEqualTo(1); // 256 >> 8
        assertThat(encoded[2] & 0xFF).isEqualTo(0);
    }

    @Test
    void testMultiplePackets_stream() throws IOException {
        var baos = new ByteArrayOutputStream();
        new MysqlPacket(0, "first".getBytes()).writeTo(baos);
        new MysqlPacket(1, "second".getBytes()).writeTo(baos);
        new MysqlPacket(2, "third".getBytes()).writeTo(baos);

        var bais = new ByteArrayInputStream(baos.toByteArray());
        assertThat(new String(MysqlPacket.readFrom(bais).payload())).isEqualTo("first");
        assertThat(new String(MysqlPacket.readFrom(bais).payload())).isEqualTo("second");
        assertThat(new String(MysqlPacket.readFrom(bais).payload())).isEqualTo("third");
    }
}
