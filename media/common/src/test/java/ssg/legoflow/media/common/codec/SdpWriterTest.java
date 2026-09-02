package ssg.legoflow.media.common.codec;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.common.sdp.*;
import static org.assertj.core.api.Assertions.*;
class SdpWriterTest {

    @Test
    void testWriteMinimalSdp() {
        SessionDescription sd = new SessionDescription(
                0,
                new Origin("-", 1, 1, "IN", "IP4", "127.0.0.1"),
                " ",
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.List.of(),
                java.util.List.of(Timing.PERMANENT),
                java.util.List.of(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.List.of(),
                java.util.List.of()
        );

        String output = SdpWriter.write(sd);

        assertThat(output).contains("v=0\r\n");
        assertThat(output).contains("o=- 1 1 IN IP4 127.0.0.1\r\n");
        assertThat(output).contains("s= \r\n");
        assertThat(output).contains("t=0 0\r\n");
        assertThat(output).doesNotContain("i=");
        assertThat(output).doesNotContain("u=");
        assertThat(output).doesNotContain("e=");
        assertThat(output).doesNotContain("p=");
        assertThat(output).doesNotContain("c=");
        assertThat(output).doesNotContain("m=");
    }

    @Test
    void testWriteWithOptionalFields() {
        SessionDescription sd = new SessionDescription(
                0,
                new Origin("alice", 123, 456, "IN", "IP4", "10.0.0.1"),
                "Test Session",
                java.util.Optional.of("Session Info"),
                java.util.Optional.of("http://example.com"),
                java.util.Optional.of("alice@example.com"),
                java.util.Optional.of("+1-555-0100"),
                java.util.Optional.of(ConnectionInfo.unicast("IN", "IP4", "10.0.0.1")),
                java.util.List.of(new Bandwidth("CT", 128)),
                java.util.List.of(Timing.PERMANENT),
                java.util.List.of(),
                java.util.Optional.empty(),
                java.util.Optional.of("base64:key"),
                java.util.List.of(Attribute.of("tool", "test")),
                java.util.List.of()
        );

        String output = SdpWriter.write(sd);

        assertThat(output).contains("i=Session Info\r\n");
        assertThat(output).contains("u=http://example.com\r\n");
        assertThat(output).contains("e=alice@example.com\r\n");
        assertThat(output).contains("p=+1-555-0100\r\n");
        assertThat(output).contains("c=IN IP4 10.0.0.1\r\n");
        assertThat(output).contains("b=CT:128\r\n");
        assertThat(output).contains("k=base64:key\r\n");
        assertThat(output).contains("a=tool:test\r\n");
    }

    @Test
    void testWriteLineOrder() {
        SessionDescription sd = new SessionDescription(
                0,
                new Origin("-", 1, 1, "IN", "IP4", "0.0.0.0"),
                "Test",
                java.util.Optional.of("Info"),
                java.util.Optional.of("http://example.com"),
                java.util.Optional.of("test@example.com"),
                java.util.Optional.of("+1-555"),
                java.util.Optional.of(ConnectionInfo.unicast("IN", "IP4", "10.0.0.1")),
                java.util.List.of(new Bandwidth("CT", 100)),
                java.util.List.of(Timing.PERMANENT),
                java.util.List.of(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.List.of(Attribute.property("recvonly")),
                java.util.List.of()
        );

        String output = SdpWriter.write(sd);
        int vPos = output.indexOf("v=");
        int oPos = output.indexOf("o=");
        int sPos = output.indexOf("s=");
        int iPos = output.indexOf("i=");
        int uPos = output.indexOf("u=");
        int ePos = output.indexOf("e=");
        int pPos = output.indexOf("p=");
        int cPos = output.indexOf("c=");
        int bPos = output.indexOf("b=");
        int tPos = output.indexOf("t=");
        int aPos = output.indexOf("a=");

        assertThat(vPos).isLessThan(oPos);
        assertThat(oPos).isLessThan(sPos);
        assertThat(sPos).isLessThan(iPos);
        assertThat(iPos).isLessThan(uPos);
        assertThat(uPos).isLessThan(ePos);
        assertThat(ePos).isLessThan(pPos);
        assertThat(pPos).isLessThan(cPos);
        assertThat(cPos).isLessThan(bPos);
        assertThat(bPos).isLessThan(tPos);
        assertThat(tPos).isLessThan(aPos);
    }

    @Test
    void testRoundTripFullSdp() {
        SessionDescription original = SdpParser.parse(SdpParserTest.FULL_SDP);
        String written = SdpWriter.write(original);
        SessionDescription reparsed = SdpParser.parse(written);

        // Verify structural equivalence
        assertThat(reparsed.version()).isEqualTo(original.version());
        assertThat(reparsed.origin().format()).isEqualTo(original.origin().format());
        assertThat(reparsed.sessionName()).isEqualTo(original.sessionName());
        assertThat(reparsed.sessionInfo()).isEqualTo(original.sessionInfo());
        assertThat(reparsed.uri()).isEqualTo(original.uri());
        assertThat(reparsed.email()).isEqualTo(original.email());
        assertThat(reparsed.phone()).isEqualTo(original.phone());
        assertThat(reparsed.timings()).isEqualTo(original.timings());
        assertThat(reparsed.mediaDescriptions()).hasSameSizeAs(original.mediaDescriptions());

        // Audio media
        MediaDescription origAudio = original.mediaDescriptions().get(0);
        MediaDescription reAudio = reparsed.mediaDescriptions().get(0);
        assertThat(reAudio.mediaType()).isEqualTo(origAudio.mediaType());
        assertThat(reAudio.port()).isEqualTo(origAudio.port());
        assertThat(reAudio.protocol()).isEqualTo(origAudio.protocol());
        assertThat(reAudio.formats()).isEqualTo(origAudio.formats());
        assertThat(reAudio.rtpMaps()).hasSameSizeAs(origAudio.rtpMaps());
        assertThat(reAudio.direction()).isEqualTo(origAudio.direction());

        // Video media
        MediaDescription origVideo = original.mediaDescriptions().get(1);
        MediaDescription reVideo = reparsed.mediaDescriptions().get(1);
        assertThat(reVideo.mediaType()).isEqualTo(origVideo.mediaType());
        assertThat(reVideo.port()).isEqualTo(origVideo.port());
        assertThat(reVideo.formats()).isEqualTo(origVideo.formats());
    }

    @Test
    void testRoundTripMinimalSdp() {
        SessionDescription original = SdpParser.parse(SdpParserTest.MINIMAL_SDP);
        String written = SdpWriter.write(original);
        SessionDescription reparsed = SdpParser.parse(written);

        assertThat(reparsed.version()).isEqualTo(original.version());
        assertThat(reparsed.origin().format()).isEqualTo(original.origin().format());
        assertThat(reparsed.sessionName()).isEqualTo(original.sessionName());
        assertThat(reparsed.mediaDescriptions()).isEmpty();
    }

    @Test
    void testWriteMediaWithBandwidth() {
        SessionDescription sd = SdpParser.parse(SdpParserTest.FULL_SDP);
        String output = SdpWriter.write(sd);

        // Session-level bandwidth
        assertThat(output).contains("b=CT:128\r\n");
        // Media-level bandwidth (within audio section)
        int audioPos = output.indexOf("m=audio");
        int videoPos = output.indexOf("m=video");
        String audioSection = output.substring(audioPos, videoPos);
        assertThat(audioSection).contains("b=AS:64\r\n");
    }

    @Test
    void testWriteMediaConnectionInfo() {
        SessionDescription sd = SdpParser.parse(SdpParserTest.FULL_SDP);
        String output = SdpWriter.write(sd);

        int audioPos = output.indexOf("m=audio");
        int videoPos = output.indexOf("m=video");
        String audioSection = output.substring(audioPos, videoPos);
        assertThat(audioSection).contains("c=IN IP4 10.0.0.2\r\n");
    }

    @Test
    void testWriteCrlfLineEndings() {
        SessionDescription sd = SdpParser.parse(SdpParserTest.MINIMAL_SDP);
        String output = SdpWriter.write(sd);

        // Every line should end with \r\n
        String[] lines = output.split("\r\n", -1);
        // Last element may be empty string after final \r\n
        assertThat(lines.length).isGreaterThanOrEqualTo(4);
    }

    @Test
    void testWriteTimezoneAdjustments() {
        SessionDescription sd = new SessionDescription(
                0,
                new Origin("-", 1, 1, "IN", "IP4", "0.0.0.0"),
                "Test",
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.List.of(),
                java.util.List.of(Timing.PERMANENT),
                java.util.List.of(),
                java.util.Optional.of("2882844526 -1h 2898848070 0"),
                java.util.Optional.empty(),
                java.util.List.of(),
                java.util.List.of()
        );

        String output = SdpWriter.write(sd);

        assertThat(output).contains("z=2882844526 -1h 2898848070 0\r\n");
    }
}
