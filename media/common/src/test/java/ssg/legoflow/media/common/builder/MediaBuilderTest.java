package ssg.legoflow.media.common.builder;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.common.sdp.*;

import static org.assertj.core.api.Assertions.*;

class MediaBuilderTest {

    @Test
    void testBuildSimpleAudio() {
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .format(0)
                .format(8)
                .direction(Direction.SENDRECV)
                .build();

        assertThat(md.mediaType()).isEqualTo(MediaType.AUDIO);
        assertThat(md.port()).isEqualTo(49170);
        assertThat(md.portCount()).isEqualTo(1);
        assertThat(md.protocol()).isEqualTo(TransportProtocol.RTP_AVP);
        assertThat(md.formats()).containsExactly(0, 8);
        assertThat(md.direction()).isEqualTo(Direction.SENDRECV);
    }

    @Test
    void testBuildWithRtpMap() {
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .rtpMap(RtpMap.of(96, "opus", 48000, 2))
                .direction(Direction.SENDRECV)
                .build();

        assertThat(md.formats()).contains(96);
        assertThat(md.rtpMaps()).hasSize(1);
        assertThat(md.rtpMaps().getFirst().codec()).isEqualTo("opus");
    }

    @Test
    void testBuildWithFmtp() {
        FormatParameters fmtp = FormatParameters.parse("96 profile-level-id=42e01f");
        MediaDescription md = new MediaBuilder(MediaType.VIDEO, 51372, TransportProtocol.RTP_AVP)
                .rtpMap(RtpMap.of(96, "H264", 90000))
                .formatParameters(fmtp)
                .direction(Direction.SENDRECV)
                .build();

        assertThat(md.formatParameters()).hasSize(1);
        assertThat(md.formatParameters().getFirst().parameters())
                .containsEntry("profile-level-id", "42e01f");
    }

    @Test
    void testBuildWithIceCandidate() {
        IceCandidate candidate = IceCandidate.parse(
                "1 1 udp 2130706431 10.0.1.1 8998 typ host");
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .format(0)
                .iceCandidate(candidate)
                .build();

        assertThat(md.iceCandidates()).hasSize(1);
        assertThat(md.iceCandidates().getFirst().type()).isEqualTo("host");
    }

    @Test
    void testBuildWithFingerprint() {
        Fingerprint fp = new Fingerprint("sha-256", "AB:CD:EF");
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_SAVPF)
                .format(0)
                .fingerprint(fp)
                .build();

        assertThat(md.fingerprint()).isPresent();
        assertThat(md.fingerprint().get().hashFunction()).isEqualTo("sha-256");
    }

    @Test
    void testBuildWithConnectionInfo() {
        ConnectionInfo ci = ConnectionInfo.unicast("IN", "IP4", "10.0.0.2");
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .format(0)
                .connectionInfo(ci)
                .build();

        assertThat(md.connectionInfo()).isPresent();
        assertThat(md.connectionInfo().get().address()).isEqualTo("10.0.0.2");
    }

    @Test
    void testBuildWithBandwidth() {
        MediaDescription md = new MediaBuilder(MediaType.VIDEO, 51372, TransportProtocol.RTP_AVP)
                .rtpMap(RtpMap.of(97, "H264", 90000))
                .bandwidth("AS", 512)
                .build();

        assertThat(md.bandwidths()).hasSize(1);
        assertThat(md.bandwidths().getFirst().modifier()).isEqualTo("AS");
        assertThat(md.bandwidths().getFirst().value()).isEqualTo(512);
    }

    @Test
    void testBuildWithTitle() {
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .format(0)
                .title("Main Audio")
                .build();

        assertThat(md.title()).hasValue("Main Audio");
    }

    @Test
    void testBuildWithGenericAttributes() {
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .format(0)
                .attribute("ptime", "20")
                .attribute("mid", "0")
                .attribute("ice-lite")
                .build();

        assertThat(md.findAttribute("ptime")).isPresent();
        assertThat(md.findAttribute("ptime").get().value()).hasValue("20");
        assertThat(md.findAttribute("ice-lite")).isPresent();
    }

    @Test
    void testBuildWithPortCount() {
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .portCount(4)
                .format(0)
                .build();

        assertThat(md.portCount()).isEqualTo(4);
    }

    @Test
    void testFormatMediaLine() {
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .format(0)
                .format(8)
                .format(96)
                .build();

        assertThat(md.formatMediaLine()).isEqualTo("audio 49170 RTP/AVP 0 8 96");
    }

    @Test
    void testFormatMediaLineWithPortCount() {
        MediaDescription md = new MediaBuilder(MediaType.VIDEO, 51372, TransportProtocol.RTP_AVP)
                .portCount(2)
                .rtpMap(RtpMap.of(97, "H264", 90000))
                .build();

        assertThat(md.formatMediaLine()).isEqualTo("video 51372/2 RTP/AVP 97");
    }

    @Test
    void testRtpMapAutoAddsFormat() {
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .rtpMap(RtpMap.of(96, "opus", 48000, 2))
                .rtpMap(RtpMap.of(97, "PCMU", 8000))
                .build();

        assertThat(md.formats()).containsExactly(96, 97);
    }

    @Test
    void testDefaultDirection() {
        MediaDescription md = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .format(0)
                .build();

        assertThat(md.direction()).isEqualTo(Direction.SENDRECV);
    }
}
