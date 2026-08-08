package ssg.legoflow.media.rtp.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive RTP demo and verifies all feature sections.
 *
 * @since 0.1.0
 */
class DemoRtpAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoRtpAll.runAll();

        assertThat(results.rtpCodec())
                .as("RTP encode/decode round-trip")
                .isTrue();

        assertThat(results.senderReport())
                .as("RTCP Sender Report encode/decode")
                .isTrue();

        assertThat(results.receiverReport())
                .as("RTCP Receiver Report encode/decode")
                .isTrue();

        assertThat(results.sourceDescription())
                .as("RTCP Source Description encode/decode")
                .isTrue();

        assertThat(results.goodbye())
                .as("RTCP Goodbye encode/decode")
                .isTrue();

        assertThat(results.compoundPacket())
                .as("Compound RTCP encode/decode")
                .isTrue();

        assertThat(results.jitterBuffer())
                .as("Jitter buffer operations")
                .isTrue();

        assertThat(results.rtpSession())
                .as("RTP session management")
                .isTrue();

        assertThat(results.rtcpInterval())
                .as("RTCP interval calculation")
                .isTrue();
    }
}
