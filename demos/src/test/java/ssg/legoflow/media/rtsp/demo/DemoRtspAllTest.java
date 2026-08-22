package ssg.legoflow.media.rtsp.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive RTSP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code RtspServer}. To test against
 * an external RTSP server (VLC, FFmpeg, Wowza), set
 * {@code DemoRtspAll.USE_EXTERNAL = true} and configure host/port before running.</p>
 */
class DemoRtspAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoRtspAll.runAll();

        assertThat(results.describe())
                .as("DESCRIBE returns valid SDP")
                .isTrue();

        assertThat(results.setup())
                .as("SETUP negotiates transport")
                .isTrue();

        assertThat(results.playPause())
                .as("PLAY and PAUSE return 200 OK")
                .isTrue();

        assertThat(results.teardown())
                .as("TEARDOWN returns 200 OK")
                .isTrue();

        assertThat(results.getParameter())
                .as("GET_PARAMETER returns 200 OK")
                .isTrue();

        assertThat(results.setParameter())
                .as("SET_PARAMETER returns 200 OK")
                .isTrue();

        assertThat(results.sdpDescription())
                .as("SDP contains valid media description")
                .isTrue();

        assertThat(results.fullWorkflow())
                .as("Full OPTIONS-to-TEARDOWN workflow")
                .isTrue();

        assertThat(results.sessionManagement())
                .as("Session ID and CSeq tracking")
                .isTrue();
    }
}
