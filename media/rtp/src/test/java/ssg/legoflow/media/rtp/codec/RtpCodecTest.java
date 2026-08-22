package ssg.legoflow.media.rtp.codec;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.rtp.packet.HeaderExtension;
import ssg.legoflow.media.rtp.packet.RtpHeader;
import ssg.legoflow.media.rtp.packet.RtpPacket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtpCodec}.
 */
class RtpCodecTest {

    @Test
    void testEncodeDecodeMinimalPacket() {
        var packet = RtpPacket.of(0, 1000, 160000L, 0x12345678L,
                new byte[]{1, 2, 3, 4, 5});
        ByteBuffer encoded = RtpCodec.encode(packet);
        assertThat(encoded.remaining()).isEqualTo(12 + 5);

        RtpPacket decoded = RtpCodec.decode(encoded);
        assertThat(decoded.header().version()).isEqualTo(2);
        assertThat(decoded.header().payloadType()).isEqualTo(0);
        assertThat(decoded.header().sequenceNumber()).isEqualTo(1000);
        assertThat(decoded.header().timestamp()).isEqualTo(160000L);
        assertThat(decoded.header().ssrc()).isEqualTo(0x12345678L);
        assertThat(decoded.payload()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void testEncodeDecodeWithMarker() {
        var packet = RtpPacket.withMarker(8, 42, 80000L, 0xABCDEF00L,
                new byte[]{10, 20, 30});
        ByteBuffer encoded = RtpCodec.encode(packet);
        RtpPacket decoded = RtpCodec.decode(encoded);

        assertThat(decoded.header().marker()).isTrue();
        assertThat(decoded.header().payloadType()).isEqualTo(8);
        assertThat(decoded.header().sequenceNumber()).isEqualTo(42);
    }

    @Test
    void testEncodeDecodeWithCsrcList() {
        var header = new RtpHeader(2, false, false, false, 0, 100, 1000L,
                0x11111111L, List.of(0x22222222L, 0x33333333L), Optional.empty());
        var packet = new RtpPacket(header, new byte[]{1});

        ByteBuffer encoded = RtpCodec.encode(packet);
        assertThat(encoded.remaining()).isEqualTo(12 + 8 + 1); // header + 2 CSRCs + payload

        RtpPacket decoded = RtpCodec.decode(encoded);
        assertThat(decoded.header().csrcCount()).isEqualTo(2);
        assertThat(decoded.header().csrcList()).containsExactly(0x22222222L, 0x33333333L);
    }

    @Test
    void testEncodeDecodeWithExtension() {
        byte[] extData = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        var ext = new HeaderExtension(0xBEDE, extData);
        var header = new RtpHeader(2, false, true, false, 96, 500, 3200L,
                0xDEADBEEFL, List.of(), Optional.of(ext));
        var packet = new RtpPacket(header, new byte[]{(byte) 0xFF});

        ByteBuffer encoded = RtpCodec.encode(packet);
        RtpPacket decoded = RtpCodec.decode(encoded);

        assertThat(decoded.header().extension()).isTrue();
        assertThat(decoded.header().headerExtension()).isPresent();
        var decodedExt = decoded.header().headerExtension().get();
        assertThat(decodedExt.profile()).isEqualTo(0xBEDE);
        assertThat(decodedExt.lengthInWords()).isEqualTo(2);
        assertThat(decodedExt.data()).containsExactly(extData);
    }

    @Test
    void testEncodeDecodeWithCsrcAndExtension() {
        byte[] extData = {0, 0, 0, 1};
        var ext = new HeaderExtension(0x1234, extData);
        var header = new RtpHeader(2, false, true, true, 111, 65535, 0xFFFFFFFFL,
                0xFFFFFFFFL, List.of(0x01L, 0x02L, 0x03L), Optional.of(ext));
        var packet = new RtpPacket(header, new byte[]{1, 2});

        ByteBuffer encoded = RtpCodec.encode(packet);
        RtpPacket decoded = RtpCodec.decode(encoded);

        assertThat(decoded.header().marker()).isTrue();
        assertThat(decoded.header().payloadType()).isEqualTo(111);
        assertThat(decoded.header().sequenceNumber()).isEqualTo(65535);
        assertThat(decoded.header().timestamp()).isEqualTo(0xFFFFFFFFL);
        assertThat(decoded.header().ssrc()).isEqualTo(0xFFFFFFFFL);
        assertThat(decoded.header().csrcList()).hasSize(3);
        assertThat(decoded.header().headerExtension()).isPresent();
    }

    @Test
    void testDecodeFromByteArray() {
        var packet = RtpPacket.of(0, 1, 160L, 0x42L, new byte[]{99});
        ByteBuffer encoded = RtpCodec.encode(packet);
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);

        RtpPacket decoded = RtpCodec.decode(bytes, 0, bytes.length);
        assertThat(decoded.header().sequenceNumber()).isEqualTo(1);
        assertThat(decoded.payload()).containsExactly(99);
    }

    @Test
    void testDecodeBufferTooSmall() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.flip();
        assertThatThrownBy(() -> RtpCodec.decode(buf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too small");
    }

    @Test
    void testRoundTripPreservesAllFields() {
        byte[] payload = new byte[160]; // 20ms of PCMU
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0xFF);
        }
        var original = RtpPacket.of(0, 32000, 2560000L, 0x87654321L, payload);

        ByteBuffer encoded = RtpCodec.encode(original);
        RtpPacket decoded = RtpCodec.decode(encoded);

        assertThat(decoded.header().version()).isEqualTo(original.header().version());
        assertThat(decoded.header().padding()).isEqualTo(original.header().padding());
        assertThat(decoded.header().extension()).isEqualTo(original.header().extension());
        assertThat(decoded.header().marker()).isEqualTo(original.header().marker());
        assertThat(decoded.header().payloadType()).isEqualTo(original.header().payloadType());
        assertThat(decoded.header().sequenceNumber()).isEqualTo(original.header().sequenceNumber());
        assertThat(decoded.header().timestamp()).isEqualTo(original.header().timestamp());
        assertThat(decoded.header().ssrc()).isEqualTo(original.header().ssrc());
        assertThat(decoded.payload()).containsExactly(payload);
    }

    @Test
    void testEmptyPayload() {
        var packet = RtpPacket.of(0, 1, 0L, 1L, new byte[0]);
        ByteBuffer encoded = RtpCodec.encode(packet);
        RtpPacket decoded = RtpCodec.decode(encoded);
        assertThat(decoded.payload()).isEmpty();
    }

    @Test
    void testMaxPayloadType() {
        var packet = RtpPacket.of(127, 1, 0L, 1L, new byte[]{1});
        ByteBuffer encoded = RtpCodec.encode(packet);
        RtpPacket decoded = RtpCodec.decode(encoded);
        assertThat(decoded.header().payloadType()).isEqualTo(127);
    }

    @Test
    void testSequenceNumberWrapAround() {
        var packet = RtpPacket.of(0, 65535, 0L, 1L, new byte[]{1});
        ByteBuffer encoded = RtpCodec.encode(packet);
        RtpPacket decoded = RtpCodec.decode(encoded);
        assertThat(decoded.header().sequenceNumber()).isEqualTo(65535);
    }
}
