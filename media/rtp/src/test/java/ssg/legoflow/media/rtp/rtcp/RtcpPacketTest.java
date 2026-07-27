package ssg.legoflow.media.rtp.rtcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for RTCP packet model classes.
 */
class RtcpPacketTest {

    @Test
    void testSenderReportCreation() {
        var sr = new SenderReport(0x11L, 1000L, 160L, 5L, 800L, List.of());
        assertThat(sr.packetType()).isEqualTo(RtcpPacket.PT_SR);
        assertThat(sr.ssrc()).isEqualTo(0x11L);
        assertThat(sr.reportCount()).isEqualTo(0);
    }

    @Test
    void testSenderReportMaxReports() {
        assertThatThrownBy(() -> {
            var reports = java.util.stream.IntStream.range(0, 32)
                    .mapToObj(i -> new ReceptionReport(i, 0, 0, 0L, 0L, 0L, 0L))
                    .toList();
            new SenderReport(1L, 0L, 0L, 0L, 0L, reports);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testReceiverReportCreation() {
        var rr = new ReceptionReport(0x22L, 50, 10, 1000L, 100L, 0L, 0L);
        var report = new ReceiverReport(0x33L, List.of(rr));
        assertThat(report.packetType()).isEqualTo(RtcpPacket.PT_RR);
        assertThat(report.reportCount()).isEqualTo(1);
    }

    @Test
    void testReceptionReportValidation() {
        assertThatThrownBy(() -> new ReceptionReport(1L, 256, 0, 0L, 0L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fraction lost");

        assertThatThrownBy(() -> new ReceptionReport(1L, -1, 0, 0L, 0L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fraction lost");
    }

    @Test
    void testSourceDescriptionCreation() {
        var chunk = new SdesChunk(0x44L,
                List.of(new SdesItem(SdesItem.Type.CNAME, "user@host")));
        var sdes = new SourceDescription(List.of(chunk));
        assertThat(sdes.packetType()).isEqualTo(RtcpPacket.PT_SDES);
        assertThat(sdes.ssrc()).isEqualTo(0x44L);
        assertThat(sdes.sourceCount()).isEqualTo(1);
    }

    @Test
    void testSourceDescriptionMustHaveChunks() {
        assertThatThrownBy(() -> new SourceDescription(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSdesChunkCname() {
        var chunk = new SdesChunk(1L, List.of(
                new SdesItem(SdesItem.Type.NAME, "Alice"),
                new SdesItem(SdesItem.Type.CNAME, "alice@example.com")));
        assertThat(chunk.cname()).hasValue("alice@example.com");
    }

    @Test
    void testSdesItemTypes() {
        assertThat(SdesItem.Type.fromCode(1)).isEqualTo(SdesItem.Type.CNAME);
        assertThat(SdesItem.Type.fromCode(6)).isEqualTo(SdesItem.Type.TOOL);
        assertThatThrownBy(() -> SdesItem.Type.fromCode(99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSdesItemValueMaxLength() {
        String longValue = "x".repeat(256);
        assertThatThrownBy(() -> new SdesItem(SdesItem.Type.CNAME, longValue))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGoodbyeCreation() {
        var bye = new Goodbye(List.of(0x55L), Optional.of("Leaving"));
        assertThat(bye.packetType()).isEqualTo(RtcpPacket.PT_BYE);
        assertThat(bye.ssrc()).isEqualTo(0x55L);
        assertThat(bye.sourceCount()).isEqualTo(1);
        assertThat(bye.reason()).hasValue("Leaving");
    }

    @Test
    void testGoodbyeMustHaveSsrc() {
        assertThatThrownBy(() -> new Goodbye(List.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGoodbyeReasonMaxLength() {
        String longReason = "x".repeat(256);
        assertThatThrownBy(() -> new Goodbye(List.of(1L), Optional.of(longReason)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testApplicationDefinedCreation() {
        var app = new ApplicationDefined(0x66L, 3, "TEST", new byte[]{1, 2, 3, 4});
        assertThat(app.packetType()).isEqualTo(RtcpPacket.PT_APP);
        assertThat(app.subtype()).isEqualTo(3);
        assertThat(app.name()).isEqualTo("TEST");
    }

    @Test
    void testApplicationDefinedValidation() {
        assertThatThrownBy(() -> new ApplicationDefined(1L, 32, "TEST", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subtype");

        assertThatThrownBy(() -> new ApplicationDefined(1L, 0, "AB", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4 ASCII");

        assertThatThrownBy(() -> new ApplicationDefined(1L, 0, "TEST", new byte[3]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple of 4");
    }

    @Test
    void testCompoundPacketCreation() {
        var sr = new SenderReport(1L, 0L, 0L, 0L, 0L, List.of());
        var sdes = new SourceDescription(List.of(
                new SdesChunk(1L, List.of(new SdesItem(SdesItem.Type.CNAME, "c@h")))));
        var compound = new CompoundPacket(List.of(sr, sdes));

        assertThat(compound.size()).isEqualTo(2);
        assertThat(compound.first()).isInstanceOf(SenderReport.class);
    }

    @Test
    void testCompoundPacketMustStartWithSrOrRr() {
        var sdes = new SourceDescription(List.of(
                new SdesChunk(1L, List.of(new SdesItem(SdesItem.Type.CNAME, "c@h")))));
        assertThatThrownBy(() -> new CompoundPacket(List.of(sdes)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SR or RR");
    }

    @Test
    void testCompoundPacketMustNotBeEmpty() {
        assertThatThrownBy(() -> new CompoundPacket(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPacketTypeConstants() {
        assertThat(RtcpPacket.PT_SR).isEqualTo(200);
        assertThat(RtcpPacket.PT_RR).isEqualTo(201);
        assertThat(RtcpPacket.PT_SDES).isEqualTo(202);
        assertThat(RtcpPacket.PT_BYE).isEqualTo(203);
        assertThat(RtcpPacket.PT_APP).isEqualTo(204);
    }

    @Test
    void testSealedInterfacePermits() {
        // Verify the sealed interface permits exactly the expected types
        RtcpPacket sr = new SenderReport(1L, 0L, 0L, 0L, 0L, List.of());
        RtcpPacket rr = new ReceiverReport(2L, List.of());
        RtcpPacket sdes = new SourceDescription(List.of(
                new SdesChunk(3L, List.of(new SdesItem(SdesItem.Type.CNAME, "c")))));
        RtcpPacket bye = new Goodbye(List.of(4L), Optional.empty());
        RtcpPacket app = new ApplicationDefined(5L, 0, "TEST", new byte[0]);

        assertThat(sr.packetType()).isEqualTo(200);
        assertThat(rr.packetType()).isEqualTo(201);
        assertThat(sdes.packetType()).isEqualTo(202);
        assertThat(bye.packetType()).isEqualTo(203);
        assertThat(app.packetType()).isEqualTo(204);
    }
}
