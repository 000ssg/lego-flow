package ssg.legoflow.media.common.codec;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.common.builder.MediaBuilder;
import ssg.legoflow.media.common.builder.SessionBuilder;
import ssg.legoflow.media.common.sdp.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class SdpNegotiatorTest {

    @Test
    void testNegotiateCompatibleAudio() {
        // Offer: PCMU (0), PCMA (8), opus (96)
        SessionDescription offer = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "10.0.0.1")
                .sessionName("Offer")
                .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "10.0.0.1"))
                .media(new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                        .format(0)
                        .format(8)
                        .rtpMap(RtpMap.of(96, "opus", 48000, 2))
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        // Answerer supports: PCMA (8), opus (96)
        SessionDescription caps = new SessionBuilder()
                .origin("-", 2, 1, "IN", "IP4", "10.0.0.2")
                .sessionName("Answer")
                .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "10.0.0.2"))
                .media(new MediaBuilder(MediaType.AUDIO, 49172, TransportProtocol.RTP_AVP)
                        .format(8)
                        .rtpMap(RtpMap.of(96, "opus", 48000, 2))
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        Optional<SessionDescription> answer = SdpNegotiator.negotiate(offer, caps);

        assertThat(answer).isPresent();
        assertThat(answer.get().mediaDescriptions()).hasSize(1);
        MediaDescription media = answer.get().mediaDescriptions().getFirst();
        assertThat(media.mediaType()).isEqualTo(MediaType.AUDIO);
        // Should include formats supported by both: 8 (static) and 96 (opus via rtpmap)
        assertThat(media.formats()).contains(8, 96);
        assertThat(media.port()).isEqualTo(49172);
    }

    @Test
    void testNegotiateNoCompatibleMedia() {
        // Offer: video only
        SessionDescription offer = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "10.0.0.1")
                .sessionName("Offer")
                .media(new MediaBuilder(MediaType.VIDEO, 51372, TransportProtocol.RTP_AVP)
                        .rtpMap(RtpMap.of(97, "H264", 90000))
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        // Answerer: audio only
        SessionDescription caps = new SessionBuilder()
                .origin("-", 2, 1, "IN", "IP4", "10.0.0.2")
                .sessionName("Answer")
                .media(new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        Optional<SessionDescription> answer = SdpNegotiator.negotiate(offer, caps);

        assertThat(answer).isEmpty();
    }

    @Test
    void testNegotiateDirectionReversed() {
        SessionDescription offer = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "10.0.0.1")
                .sessionName("Offer")
                .media(new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.SENDONLY)
                        .build())
                .build();

        SessionDescription caps = new SessionBuilder()
                .origin("-", 2, 1, "IN", "IP4", "10.0.0.2")
                .sessionName("Answer")
                .media(new MediaBuilder(MediaType.AUDIO, 49172, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        Optional<SessionDescription> answer = SdpNegotiator.negotiate(offer, caps);

        assertThat(answer).isPresent();
        assertThat(answer.get().mediaDescriptions().getFirst().direction())
                .isEqualTo(Direction.RECVONLY);
    }

    @Test
    void testNegotiateInactivePreserved() {
        SessionDescription offer = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "10.0.0.1")
                .sessionName("Offer")
                .media(new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.INACTIVE)
                        .build())
                .build();

        SessionDescription caps = new SessionBuilder()
                .origin("-", 2, 1, "IN", "IP4", "10.0.0.2")
                .sessionName("Answer")
                .media(new MediaBuilder(MediaType.AUDIO, 49172, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        Optional<SessionDescription> answer = SdpNegotiator.negotiate(offer, caps);

        assertThat(answer).isPresent();
        assertThat(answer.get().mediaDescriptions().getFirst().direction())
                .isEqualTo(Direction.INACTIVE);
    }

    @Test
    void testNegotiateMultipleMedia() {
        // Offer: audio + video
        SessionDescription offer = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "10.0.0.1")
                .sessionName("Offer")
                .media(new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.SENDRECV)
                        .build())
                .media(new MediaBuilder(MediaType.VIDEO, 51372, TransportProtocol.RTP_AVP)
                        .rtpMap(RtpMap.of(97, "H264", 90000))
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        // Answerer: audio only
        SessionDescription caps = new SessionBuilder()
                .origin("-", 2, 1, "IN", "IP4", "10.0.0.2")
                .sessionName("Answer")
                .media(new MediaBuilder(MediaType.AUDIO, 49172, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        Optional<SessionDescription> answer = SdpNegotiator.negotiate(offer, caps);

        assertThat(answer).isPresent();
        assertThat(answer.get().mediaDescriptions()).hasSize(2);

        // Audio accepted
        MediaDescription audio = answer.get().mediaDescriptions().get(0);
        assertThat(audio.port()).isGreaterThan(0);
        assertThat(audio.formats()).contains(0);

        // Video rejected (port 0)
        MediaDescription video = answer.get().mediaDescriptions().get(1);
        assertThat(video.port()).isZero();
    }

    @Test
    void testNegotiateProtocolMismatch() {
        SessionDescription offer = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "10.0.0.1")
                .sessionName("Offer")
                .media(new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_SAVPF)
                        .format(0)
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        SessionDescription caps = new SessionBuilder()
                .origin("-", 2, 1, "IN", "IP4", "10.0.0.2")
                .sessionName("Answer")
                .media(new MediaBuilder(MediaType.AUDIO, 49172, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        Optional<SessionDescription> answer = SdpNegotiator.negotiate(offer, caps);

        assertThat(answer).isEmpty();
    }

    @Test
    void testNegotiateRecvonlyReversed() {
        SessionDescription offer = new SessionBuilder()
                .origin("-", 1, 1, "IN", "IP4", "10.0.0.1")
                .sessionName("Offer")
                .media(new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.RECVONLY)
                        .build())
                .build();

        SessionDescription caps = new SessionBuilder()
                .origin("-", 2, 1, "IN", "IP4", "10.0.0.2")
                .sessionName("Answer")
                .media(new MediaBuilder(MediaType.AUDIO, 49172, TransportProtocol.RTP_AVP)
                        .format(0)
                        .direction(Direction.SENDRECV)
                        .build())
                .build();

        Optional<SessionDescription> answer = SdpNegotiator.negotiate(offer, caps);

        assertThat(answer).isPresent();
        assertThat(answer.get().mediaDescriptions().getFirst().direction())
                .isEqualTo(Direction.SENDONLY);
    }
}
