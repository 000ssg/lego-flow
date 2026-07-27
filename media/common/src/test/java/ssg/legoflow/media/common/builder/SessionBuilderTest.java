package ssg.legoflow.media.common.builder;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.common.sdp.*;

import static org.assertj.core.api.Assertions.*;

class SessionBuilderTest {

    @Test
    void testBuildMinimalSession() {
        SessionDescription sd = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "0.0.0.0")
                .sessionName("Test")
                .build();

        assertThat(sd.version()).isZero();
        assertThat(sd.origin().username()).isEqualTo("-");
        assertThat(sd.sessionName()).isEqualTo("Test");
        assertThat(sd.timings()).hasSize(1); // default PERMANENT
        assertThat(sd.timings().getFirst()).isEqualTo(Timing.PERMANENT);
    }

    @Test
    void testBuildWithAllOptionals() {
        SessionDescription sd = new SessionBuilder()
                .origin("alice", 123, 456, "IN", "IP4", "10.0.0.1")
                .sessionName("Full Session")
                .sessionInfo("A detailed description")
                .uri("http://example.com")
                .email("alice@example.com")
                .phone("+1-555-0100")
                .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "10.0.0.1"))
                .bandwidth("CT", 128)
                .timing(new Timing(1000, 2000))
                .attribute("tool", "lego-flow")
                .build();

        assertThat(sd.sessionInfo()).hasValue("A detailed description");
        assertThat(sd.uri()).hasValue("http://example.com");
        assertThat(sd.email()).hasValue("alice@example.com");
        assertThat(sd.phone()).hasValue("+1-555-0100");
        assertThat(sd.connectionInfo()).isPresent();
        assertThat(sd.bandwidths()).hasSize(1);
        assertThat(sd.timings()).hasSize(1);
        assertThat(sd.timings().getFirst().startTime()).isEqualTo(1000);
        assertThat(sd.attributes()).hasSize(1);
        assertThat(sd.findAttribute("tool").get().value()).hasValue("lego-flow");
    }

    @Test
    void testBuildWithMedia() {
        MediaDescription audio = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .format(0)
                .format(8)
                .direction(Direction.SENDRECV)
                .build();

        SessionDescription sd = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "10.0.0.1")
                .sessionName("With Media")
                .media(audio)
                .build();

        assertThat(sd.mediaDescriptions()).hasSize(1);
        assertThat(sd.mediaDescriptions().getFirst().mediaType()).isEqualTo(MediaType.AUDIO);
    }

    @Test
    void testBuildWithOriginRecord() {
        Origin origin = new Origin("bob", 999, 1, "IN", "IP6", "::1");
        SessionDescription sd = new SessionBuilder()
                .origin(origin)
                .sessionName("IPv6")
                .build();

        assertThat(sd.origin()).isEqualTo(origin);
    }

    @Test
    void testBuildMissingOriginThrows() {
        assertThatThrownBy(() -> new SessionBuilder().sessionName("Test").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Origin is required");
    }

    @Test
    void testBuildWithPropertyAttribute() {
        SessionDescription sd = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "0.0.0.0")
                .attribute("recvonly")
                .build();

        assertThat(sd.findAttribute("recvonly")).isPresent();
        assertThat(sd.findAttribute("recvonly").get().value()).isEmpty();
    }

    @Test
    void testBuildWithTimezoneAndEncryption() {
        SessionDescription sd = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "0.0.0.0")
                .timezoneAdjustments("2882844526 -1h")
                .encryptionKey("base64:abc")
                .build();

        assertThat(sd.timezoneAdjustments()).hasValue("2882844526 -1h");
        assertThat(sd.encryptionKey()).hasValue("base64:abc");
    }

    @Test
    void testBuildWithRepeatTime() {
        SessionDescription sd = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "0.0.0.0")
                .timing(new Timing(1000, 2000))
                .repeatTime(RepeatTime.parse("604800 3600 0 90000"))
                .build();

        assertThat(sd.repeatTimes()).hasSize(1);
        assertThat(sd.repeatTimes().getFirst().repeatInterval()).isEqualTo("604800");
    }

    @Test
    void testDefaultSessionNameIsSpace() {
        SessionDescription sd = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "0.0.0.0")
                .build();

        assertThat(sd.sessionName()).isEqualTo(" ");
    }

    @Test
    void testBuildWithMultipleMedia() {
        SessionDescription sd = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "10.0.0.1")
                .sessionName("Multi")
                .media(new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                        .format(0).build())
                .media(new MediaBuilder(MediaType.VIDEO, 51372, TransportProtocol.RTP_AVP)
                        .rtpMap(RtpMap.of(97, "H264", 90000)).build())
                .build();

        assertThat(sd.mediaDescriptions()).hasSize(2);
    }
}
