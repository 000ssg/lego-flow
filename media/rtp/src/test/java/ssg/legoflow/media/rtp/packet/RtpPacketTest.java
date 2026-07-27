package ssg.legoflow.media.rtp.packet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RtpPacket}, {@link RtpHeader}, and {@link HeaderExtension}.
 */
class RtpPacketTest {

    @Test
    void testCreateMinimalPacket() {
        var packet = RtpPacket.of(0, 1, 160L, 0x12345678L, new byte[]{1, 2, 3});
        assertThat(packet.header().version()).isEqualTo(2);
        assertThat(packet.header().payloadType()).isEqualTo(0);
        assertThat(packet.header().sequenceNumber()).isEqualTo(1);
        assertThat(packet.header().timestamp()).isEqualTo(160L);
        assertThat(packet.header().ssrc()).isEqualTo(0x12345678L);
        assertThat(packet.payload()).containsExactly(1, 2, 3);
    }

    @Test
    void testCreateWithMarker() {
        var packet = RtpPacket.withMarker(96, 500, 8000L, 0xABCDL, new byte[]{0});
        assertThat(packet.header().marker()).isTrue();
    }

    @Test
    void testPayloadImmutability() {
        byte[] original = {1, 2, 3};
        var packet = RtpPacket.of(0, 1, 0L, 1L, original);
        original[0] = 99; // modify original
        assertThat(packet.payload()[0]).isEqualTo((byte) 1); // should be unchanged

        byte[] retrieved = packet.payload();
        retrieved[0] = 99;
        assertThat(packet.payload()[0]).isEqualTo((byte) 1); // still unchanged
    }

    @Test
    void testTotalSize() {
        var packet = RtpPacket.of(0, 1, 0L, 1L, new byte[160]);
        assertThat(packet.totalSize()).isEqualTo(12 + 160);
        assertThat(packet.payloadSize()).isEqualTo(160);
    }

    @Test
    void testEquality() {
        var p1 = RtpPacket.of(0, 1, 160L, 1L, new byte[]{1, 2});
        var p2 = RtpPacket.of(0, 1, 160L, 1L, new byte[]{1, 2});
        var p3 = RtpPacket.of(0, 2, 160L, 1L, new byte[]{1, 2});

        assertThat(p1).isEqualTo(p2);
        assertThat(p1).isNotEqualTo(p3);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    void testToString() {
        var packet = RtpPacket.of(0, 42, 160L, 0x12345678L, new byte[80]);
        assertThat(packet.toString()).contains("pt=0", "seq=42", "payload=80 bytes");
    }

    @Test
    void testHeaderValidation() {
        assertThatThrownBy(() -> new RtpHeader(1, false, false, false,
                0, 0, 0L, 0L, List.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");

        assertThatThrownBy(() -> new RtpHeader(2, false, false, false,
                128, 0, 0L, 0L, List.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload type");

        assertThatThrownBy(() -> new RtpHeader(2, false, false, false,
                0, -1, 0L, 0L, List.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sequence number");
    }

    @Test
    void testHeaderExtensionBitConsistency() {
        assertThatThrownBy(() -> new RtpHeader(2, false, true, false,
                0, 0, 0L, 0L, List.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Extension bit");
    }

    @Test
    void testHeaderExtensionValidation() {
        assertThatThrownBy(() -> new HeaderExtension(0x10000, new byte[4]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Profile");

        assertThatThrownBy(() -> new HeaderExtension(0, new byte[3]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple of 4");
    }

    @Test
    void testHeaderExtensionEquality() {
        var ext1 = new HeaderExtension(0xBEDE, new byte[]{1, 2, 3, 4});
        var ext2 = new HeaderExtension(0xBEDE, new byte[]{1, 2, 3, 4});
        var ext3 = new HeaderExtension(0xBEDE, new byte[]{5, 6, 7, 8});

        assertThat(ext1).isEqualTo(ext2);
        assertThat(ext1).isNotEqualTo(ext3);
    }

    @Test
    void testMaxCsrcCount() {
        assertThatThrownBy(() -> new RtpHeader(2, false, false, false,
                0, 0, 0L, 0L,
                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L,
                        9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSRC");
    }

    @Test
    void testNullPayload() {
        assertThatThrownBy(() -> new RtpPacket(
                new RtpHeader(2, false, false, false, 0, 0, 0L, 0L,
                        List.of(), Optional.empty()), null))
                .isInstanceOf(NullPointerException.class);
    }
}
