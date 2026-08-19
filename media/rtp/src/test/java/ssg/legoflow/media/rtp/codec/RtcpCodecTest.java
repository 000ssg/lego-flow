package ssg.legoflow.media.rtp.codec;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.rtp.rtcp.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtcpCodec}.
 */
class RtcpCodecTest {

    @Test
    void testEncodDecodeSenderReport() {
        var rr = new ReceptionReport(0xAAAAAAAAL, 25, 100,
                50000L, 320L, 0x12340000L, 5000L);
        var sr = new SenderReport(0x11111111L, 0x1234567890ABCDEFL,
                160000L, 500L, 80000L, List.of(rr));

        ByteBuffer encoded = RtcpCodec.encode(sr);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(SenderReport.class);
        var decodedSr = (SenderReport) decoded;
        assertThat(decodedSr.ssrc()).isEqualTo(0x11111111L);
        assertThat(decodedSr.ntpTimestamp()).isEqualTo(0x1234567890ABCDEFL);
        assertThat(decodedSr.rtpTimestamp()).isEqualTo(160000L);
        assertThat(decodedSr.senderPacketCount()).isEqualTo(500L);
        assertThat(decodedSr.senderOctetCount()).isEqualTo(80000L);
        assertThat(decodedSr.reports()).hasSize(1);
        assertThat(decodedSr.reports().getFirst().ssrc()).isEqualTo(0xAAAAAAAAL);
        assertThat(decodedSr.reports().getFirst().fractionLost()).isEqualTo(25);
        assertThat(decodedSr.reports().getFirst().cumulativeLost()).isEqualTo(100);
    }

    @Test
    void testEncodeDecodeSenderReportNoReports() {
        var sr = new SenderReport(0x22222222L, 0L, 0L, 0L, 0L, List.of());
        ByteBuffer encoded = RtcpCodec.encode(sr);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(SenderReport.class);
        var decodedSr = (SenderReport) decoded;
        assertThat(decodedSr.ssrc()).isEqualTo(0x22222222L);
        assertThat(decodedSr.reports()).isEmpty();
    }

    @Test
    void testEncodeDecodeReceiverReport() {
        var rr1 = new ReceptionReport(0xBBBBBBBBL, 128, -10,
                65536L, 100L, 0L, 0L);
        var rr2 = new ReceptionReport(0xCCCCCCCCL, 0, 0,
                1000L, 50L, 0x56780000L, 3000L);
        var receiverReport = new ReceiverReport(0x33333333L, List.of(rr1, rr2));

        ByteBuffer encoded = RtcpCodec.encode(receiverReport);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(ReceiverReport.class);
        var decodedRr = (ReceiverReport) decoded;
        assertThat(decodedRr.ssrc()).isEqualTo(0x33333333L);
        assertThat(decodedRr.reports()).hasSize(2);
        assertThat(decodedRr.reports().get(0).fractionLost()).isEqualTo(128);
        assertThat(decodedRr.reports().get(0).cumulativeLost()).isEqualTo(-10);
        assertThat(decodedRr.reports().get(1).ssrc()).isEqualTo(0xCCCCCCCCL);
    }

    @Test
    void testEncodeDecodeSourceDescription() {
        var items = List.of(
                new SdesItem(SdesItem.Type.CNAME, "user@host.example.com"),
                new SdesItem(SdesItem.Type.NAME, "Test User"),
                new SdesItem(SdesItem.Type.TOOL, "LegoFlow/1.0")
        );
        var chunk = new SdesChunk(0x44444444L, items);
        var sdes = new SourceDescription(List.of(chunk));

        ByteBuffer encoded = RtcpCodec.encode(sdes);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(SourceDescription.class);
        var decodedSdes = (SourceDescription) decoded;
        assertThat(decodedSdes.chunks()).hasSize(1);
        var decodedChunk = decodedSdes.chunks().getFirst();
        assertThat(decodedChunk.ssrc()).isEqualTo(0x44444444L);
        assertThat(decodedChunk.items()).hasSize(3);
        assertThat(decodedChunk.cname()).hasValue("user@host.example.com");
        assertThat(decodedChunk.items().get(1).value()).isEqualTo("Test User");
        assertThat(decodedChunk.items().get(2).type()).isEqualTo(SdesItem.Type.TOOL);
    }

    @Test
    void testEncodeDecodeSourceDescriptionMultipleChunks() {
        var chunk1 = new SdesChunk(0x11L,
                List.of(new SdesItem(SdesItem.Type.CNAME, "a@b.com")));
        var chunk2 = new SdesChunk(0x22L,
                List.of(new SdesItem(SdesItem.Type.CNAME, "c@d.com")));
        var sdes = new SourceDescription(List.of(chunk1, chunk2));

        ByteBuffer encoded = RtcpCodec.encode(sdes);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(SourceDescription.class);
        var decodedSdes = (SourceDescription) decoded;
        assertThat(decodedSdes.chunks()).hasSize(2);
        assertThat(decodedSdes.chunks().get(0).cname()).hasValue("a@b.com");
        assertThat(decodedSdes.chunks().get(1).cname()).hasValue("c@d.com");
    }

    @Test
    void testEncodeDecodeGoodbye() {
        var bye = new Goodbye(List.of(0x55555555L, 0x66666666L),
                Optional.of("Session ending"));

        ByteBuffer encoded = RtcpCodec.encode(bye);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(Goodbye.class);
        var decodedBye = (Goodbye) decoded;
        assertThat(decodedBye.ssrcList()).containsExactly(0x55555555L, 0x66666666L);
        assertThat(decodedBye.reason()).hasValue("Session ending");
    }

    @Test
    void testEncodeDecodeGoodbyeNoReason() {
        var bye = new Goodbye(List.of(0x77777777L), Optional.empty());

        ByteBuffer encoded = RtcpCodec.encode(bye);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(Goodbye.class);
        var decodedBye = (Goodbye) decoded;
        assertThat(decodedBye.ssrcList()).containsExactly(0x77777777L);
        assertThat(decodedBye.reason()).isEmpty();
    }

    @Test
    void testEncodeDecodeApplicationDefined() {
        byte[] appData = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
        var app = new ApplicationDefined(0x88888888L, 5, "TEST", appData);

        ByteBuffer encoded = RtcpCodec.encode(app);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(ApplicationDefined.class);
        var decodedApp = (ApplicationDefined) decoded;
        assertThat(decodedApp.ssrc()).isEqualTo(0x88888888L);
        assertThat(decodedApp.subtype()).isEqualTo(5);
        assertThat(decodedApp.name()).isEqualTo("TEST");
        assertThat(decodedApp.data()).containsExactly(appData);
    }

    @Test
    void testEncodeDecodeApplicationDefinedNoData() {
        var app = new ApplicationDefined(0x99999999L, 0, "NOOP", new byte[0]);
        ByteBuffer encoded = RtcpCodec.encode(app);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(ApplicationDefined.class);
        var decodedApp = (ApplicationDefined) decoded;
        assertThat(decodedApp.name()).isEqualTo("NOOP");
        assertThat(decodedApp.data()).isEmpty();
    }

    @Test
    void testEncodeDecodeCompoundPacket() {
        var sr = new SenderReport(0x11111111L, 1000L, 160000L, 10L, 1600L, List.of());
        var sdes = new SourceDescription(List.of(
                new SdesChunk(0x11111111L,
                        List.of(new SdesItem(SdesItem.Type.CNAME, "test@example.com")))));
        var compound = new CompoundPacket(List.of(sr, sdes));

        ByteBuffer encoded = RtcpCodec.encodeCompound(compound);
        CompoundPacket decoded = RtcpCodec.decodeCompound(encoded);

        assertThat(decoded.size()).isEqualTo(2);
        assertThat(decoded.first()).isInstanceOf(SenderReport.class);
        assertThat(decoded.packets().get(1)).isInstanceOf(SourceDescription.class);
    }

    @Test
    void testDecodeBufferTooSmall() {
        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.flip();
        assertThatThrownBy(() -> RtcpCodec.decode(buf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too small");
    }

    @Test
    void testDecodeUnknownPacketType() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.put((byte) 0x80); // V=2, count=0
        buf.put((byte) 199);  // unknown PT
        buf.putShort((short) 1); // length=1 word
        buf.putInt(0);
        buf.flip();

        assertThatThrownBy(() -> RtcpCodec.decode(buf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown");
    }

    @Test
    void testReceptionReportNegativeLost() {
        var rr = new ReceptionReport(0x11L, 0, -1, 100L, 0L, 0L, 0L);
        var receiverReport = new ReceiverReport(0x22L, List.of(rr));

        ByteBuffer encoded = RtcpCodec.encode(receiverReport);
        var decoded = (ReceiverReport) RtcpCodec.decode(encoded);
        assertThat(decoded.reports().getFirst().cumulativeLost()).isEqualTo(-1);
    }
}
