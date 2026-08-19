package ssg.legoflow.media.common.codec;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.common.sdp.*;
import static org.assertj.core.api.Assertions.*;
class SdpParserTest {

    static final String FULL_SDP = """
            v=0\r
            o=alice 2890844526 2890842807 IN IP4 10.0.0.1\r
            s=Audio/Video Call\r
            i=A test session\r
            u=http://example.com/session\r
            e=alice@example.com\r
            p=+1-555-0100\r
            c=IN IP4 10.0.0.1\r
            b=CT:128\r
            t=0 0\r
            a=group:BUNDLE 0 1\r
            a=ice-ufrag:abc123\r
            a=ice-pwd:def456\r
            m=audio 49170 RTP/AVP 0 8 96\r
            c=IN IP4 10.0.0.2\r
            b=AS:64\r
            a=rtpmap:0 PCMU/8000\r
            a=rtpmap:8 PCMA/8000\r
            a=rtpmap:96 opus/48000/2\r
            a=fmtp:96 minptime=10;useinbandfec=1\r
            a=ptime:20\r
            a=sendrecv\r
            a=mid:0\r
            m=video 51372 RTP/AVP 97\r
            a=rtpmap:97 H264/90000\r
            a=fmtp:97 profile-level-id=42e01f;packetization-mode=1\r
            a=framerate:30\r
            a=sendrecv\r
            a=mid:1\r
            """;

    static final String MINIMAL_SDP = """
            v=0\r
            o=- 1 1 IN IP4 127.0.0.1\r
            s= \r
            t=0 0\r
            """;

    @Test
    void testParseFullSdp() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);

        assertThat(sd.version()).isZero();
        assertThat(sd.origin().username()).isEqualTo("alice");
        assertThat(sd.origin().sessionId()).isEqualTo(2890844526L);
        assertThat(sd.sessionName()).isEqualTo("Audio/Video Call");
        assertThat(sd.sessionInfo()).hasValue("A test session");
        assertThat(sd.uri()).hasValue("http://example.com/session");
        assertThat(sd.email()).hasValue("alice@example.com");
        assertThat(sd.phone()).hasValue("+1-555-0100");
        assertThat(sd.connectionInfo()).isPresent();
        assertThat(sd.connectionInfo().get().address()).isEqualTo("10.0.0.1");
        assertThat(sd.bandwidths()).hasSize(1);
        assertThat(sd.bandwidths().getFirst().modifier()).isEqualTo("CT");
        assertThat(sd.timings()).hasSize(1);
        assertThat(sd.timings().getFirst()).isEqualTo(Timing.PERMANENT);
    }

    @Test
    void testParseSessionAttributes() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);

        assertThat(sd.attributes()).hasSize(3);
        assertThat(sd.findAttribute("group")).isPresent();
        assertThat(sd.findAttribute("group").get().value()).hasValue("BUNDLE 0 1");
        assertThat(sd.findAttribute("ice-ufrag")).isPresent();
        assertThat(sd.findAttribute("ice-pwd")).isPresent();
    }

    @Test
    void testParseAudioMedia() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);
        MediaDescription audio = sd.mediaDescriptions().get(0);

        assertThat(audio.mediaType()).isEqualTo(MediaType.AUDIO);
        assertThat(audio.port()).isEqualTo(49170);
        assertThat(audio.protocol()).isEqualTo(TransportProtocol.RTP_AVP);
        assertThat(audio.formats()).containsExactly(0, 8, 96);
        assertThat(audio.connectionInfo()).isPresent();
        assertThat(audio.connectionInfo().get().address()).isEqualTo("10.0.0.2");
        assertThat(audio.bandwidths()).hasSize(1);
        assertThat(audio.direction()).isEqualTo(Direction.SENDRECV);
    }

    @Test
    void testParseAudioRtpMaps() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);
        MediaDescription audio = sd.mediaDescriptions().get(0);

        assertThat(audio.rtpMaps()).hasSize(3);
        assertThat(audio.findRtpMap(0).get().codec()).isEqualTo("PCMU");
        assertThat(audio.findRtpMap(8).get().codec()).isEqualTo("PCMA");
        assertThat(audio.findRtpMap(96).get().codec()).isEqualTo("opus");
        assertThat(audio.findRtpMap(96).get().channels()).hasValue(2);
    }

    @Test
    void testParseAudioFmtp() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);
        MediaDescription audio = sd.mediaDescriptions().get(0);

        assertThat(audio.formatParameters()).hasSize(1);
        FormatParameters fmtp = audio.formatParameters().getFirst();
        assertThat(fmtp.payloadType()).isEqualTo(96);
        assertThat(fmtp.parameters()).containsEntry("minptime", "10");
        assertThat(fmtp.parameters()).containsEntry("useinbandfec", "1");
    }

    @Test
    void testParseVideoMedia() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);
        MediaDescription video = sd.mediaDescriptions().get(1);

        assertThat(video.mediaType()).isEqualTo(MediaType.VIDEO);
        assertThat(video.port()).isEqualTo(51372);
        assertThat(video.formats()).containsExactly(97);
        assertThat(video.rtpMaps()).hasSize(1);
        assertThat(video.rtpMaps().getFirst().codec()).isEqualTo("H264");
        assertThat(video.direction()).isEqualTo(Direction.SENDRECV);
    }

    @Test
    void testParseVideoFmtp() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);
        MediaDescription video = sd.mediaDescriptions().get(1);

        assertThat(video.formatParameters()).hasSize(1);
        FormatParameters fmtp = video.formatParameters().getFirst();
        assertThat(fmtp.parameters()).containsEntry("profile-level-id", "42e01f");
        assertThat(fmtp.parameters()).containsEntry("packetization-mode", "1");
    }

    @Test
    void testParseMinimalSdp() {
        SessionDescription sd = SdpParser.parse(MINIMAL_SDP);

        assertThat(sd.version()).isZero();
        assertThat(sd.origin().username()).isEqualTo("-");
        assertThat(sd.sessionName()).isEqualTo(" ");
        assertThat(sd.sessionInfo()).isEmpty();
        assertThat(sd.uri()).isEmpty();
        assertThat(sd.email()).isEmpty();
        assertThat(sd.phone()).isEmpty();
        assertThat(sd.connectionInfo()).isEmpty();
        assertThat(sd.bandwidths()).isEmpty();
        assertThat(sd.timings()).hasSize(1);
        assertThat(sd.attributes()).isEmpty();
        assertThat(sd.mediaDescriptions()).isEmpty();
    }

    @Test
    void testParseMissingOrigin() {
        assertThatThrownBy(() -> SdpParser.parse("v=0\r\ns= \r\nt=0 0\r\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o= line");
    }

    @Test
    void testParseMissingSessionName() {
        assertThatThrownBy(() -> SdpParser.parse("v=0\r\no=- 1 1 IN IP4 0.0.0.0\r\nt=0 0\r\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s= line");
    }

    @Test
    void testParseUnknownAttributesPreserved() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                m=audio 5004 RTP/AVP 0\r
                a=custom-attr:some-value\r
                a=another-flag\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);
        MediaDescription media = sd.mediaDescriptions().getFirst();

        assertThat(media.findAttribute("custom-attr")).isPresent();
        assertThat(media.findAttribute("custom-attr").get().value()).hasValue("some-value");
        assertThat(media.findAttribute("another-flag")).isPresent();
        assertThat(media.findAttribute("another-flag").get().value()).isEmpty();
    }

    @Test
    void testParseDirectionSendonly() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                m=audio 5004 RTP/AVP 0\r
                a=sendonly\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.mediaDescriptions().getFirst().direction()).isEqualTo(Direction.SENDONLY);
    }

    @Test
    void testParseDirectionRecvonly() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                m=audio 5004 RTP/AVP 0\r
                a=recvonly\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.mediaDescriptions().getFirst().direction()).isEqualTo(Direction.RECVONLY);
    }

    @Test
    void testParseDirectionInactive() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                m=audio 5004 RTP/AVP 0\r
                a=inactive\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.mediaDescriptions().getFirst().direction()).isEqualTo(Direction.INACTIVE);
    }

    @Test
    void testParseDefaultDirectionSendrecv() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                m=audio 5004 RTP/AVP 0\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.mediaDescriptions().getFirst().direction()).isEqualTo(Direction.SENDRECV);
    }

    @Test
    void testParseIceCandidates() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                m=audio 5004 RTP/AVP 0\r
                a=candidate:1 1 udp 2130706431 10.0.1.1 8998 typ host\r
                a=candidate:2 1 udp 1694498815 192.0.2.3 45664 typ srflx raddr 10.0.1.1 rport 8998\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);
        MediaDescription media = sd.mediaDescriptions().getFirst();

        assertThat(media.iceCandidates()).hasSize(2);
        assertThat(media.iceCandidates().get(0).type()).isEqualTo("host");
        assertThat(media.iceCandidates().get(1).type()).isEqualTo("srflx");
    }

    @Test
    void testParseFingerprint() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                m=audio 5004 RTP/AVP 0\r
                a=fingerprint:sha-256 AB:CD:EF\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);
        MediaDescription media = sd.mediaDescriptions().getFirst();

        assertThat(media.fingerprint()).isPresent();
        assertThat(media.fingerprint().get().hashFunction()).isEqualTo("sha-256");
        assertThat(media.fingerprint().get().hashValue()).isEqualTo("AB:CD:EF");
    }

    @Test
    void testParseMediaWithPortCount() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                m=audio 49170/2 RTP/AVP 0\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);
        MediaDescription media = sd.mediaDescriptions().getFirst();

        assertThat(media.port()).isEqualTo(49170);
        assertThat(media.portCount()).isEqualTo(2);
    }

    @Test
    void testParseWithEncryptionKey() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                k=base64:somekey\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.encryptionKey()).hasValue("base64:somekey");
    }

    @Test
    void testParseWithTimezoneAdjustments() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                z=2882844526 -1h 2898848070 0\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.timezoneAdjustments()).hasValue("2882844526 -1h 2898848070 0");
    }

    @Test
    void testParseEmptyLinesIgnored() {
        String sdp = """
                v=0\r
                \r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                \r
                t=0 0\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.version()).isZero();
        assertThat(sd.sessionName()).isEqualTo("Test");
    }

    @Test
    void testParseLfLineEndings() {
        String sdp = "v=0\no=- 1 1 IN IP4 0.0.0.0\ns=Test\nt=0 0\n";
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.version()).isZero();
        assertThat(sd.sessionName()).isEqualTo("Test");
    }

    @Test
    void testParseMultipleTimings() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=3034423619 3042462419\r
                t=3042462419 3050501219\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.timings()).hasSize(2);
    }

    @Test
    void testParseRepeatTimes() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                r=604800 3600 0 90000\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.repeatTimes()).hasSize(1);
        assertThat(sd.repeatTimes().getFirst().repeatInterval()).isEqualTo("604800");
    }

    @Test
    void testParseMultipleMediaDescriptions() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);

        assertThat(sd.mediaDescriptions()).hasSize(2);
        assertThat(sd.mediaDescriptions().get(0).mediaType()).isEqualTo(MediaType.AUDIO);
        assertThat(sd.mediaDescriptions().get(1).mediaType()).isEqualTo(MediaType.VIDEO);
    }

    @Test
    void testEffectiveConnectionInfo() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);

        // Audio has media-level c=
        MediaDescription audio = sd.mediaDescriptions().get(0);
        assertThat(sd.effectiveConnectionInfo(audio).get().address()).isEqualTo("10.0.0.2");

        // Video has no media-level c=, falls back to session-level
        MediaDescription video = sd.mediaDescriptions().get(1);
        assertThat(sd.effectiveConnectionInfo(video).get().address()).isEqualTo("10.0.0.1");
    }

    @Test
    void testParseMediaTitle() {
        String sdp = """
                v=0\r
                o=- 1 1 IN IP4 0.0.0.0\r
                s=Test\r
                t=0 0\r
                m=audio 5004 RTP/AVP 0\r
                i=Audio Stream\r
                """;
        SessionDescription sd = SdpParser.parse(sdp);

        assertThat(sd.mediaDescriptions().getFirst().title()).hasValue("Audio Stream");
    }

    @Test
    void testFindAttributes() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);
        MediaDescription audio = sd.mediaDescriptions().get(0);

        assertThat(audio.findAttributes("rtpmap")).hasSize(3);
        assertThat(audio.findAttribute("ptime")).isPresent();
        assertThat(audio.findAttribute("ptime").get().value()).hasValue("20");
        assertThat(audio.findAttribute("nonexistent")).isEmpty();
    }

    @Test
    void testParseBandwidthAtSessionAndMediaLevel() {
        SessionDescription sd = SdpParser.parse(FULL_SDP);

        assertThat(sd.bandwidths()).hasSize(1);
        assertThat(sd.bandwidths().getFirst().modifier()).isEqualTo("CT");
        assertThat(sd.bandwidths().getFirst().value()).isEqualTo(128);

        MediaDescription audio = sd.mediaDescriptions().get(0);
        assertThat(audio.bandwidths()).hasSize(1);
        assertThat(audio.bandwidths().getFirst().modifier()).isEqualTo("AS");
        assertThat(audio.bandwidths().getFirst().value()).isEqualTo(64);
    }
}
