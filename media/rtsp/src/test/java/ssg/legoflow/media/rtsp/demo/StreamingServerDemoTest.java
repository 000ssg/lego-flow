package ssg.legoflow.media.rtsp.demo;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.rtsp.protocol.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StreamingServerDemo}.
 */
class StreamingServerDemoTest {

    @Test
    void testCreateServer() {
        var server = StreamingServerDemo.createServer(8554);
        assertThat(server).isNotNull();
        assertThat(server.port()).isEqualTo(8554);
    }

    @Test
    void testTestMediaSourcePath() {
        var source = new StreamingServerDemo.TestMediaSource();
        assertThat(source.path()).isEqualTo("/test/stream");
    }

    @Test
    void testTestMediaSourceDescribe() {
        var source = new StreamingServerDemo.TestMediaSource();
        var sdp = source.describe();
        assertThat(sdp).isNotNull();
        assertThat(sdp.sessionName()).isEqualTo("Test Stream");
        assertThat(sdp.mediaDescriptions()).hasSize(1);
        assertThat(sdp.mediaDescriptions().getFirst().rtpMaps()).hasSize(1);
        assertThat(sdp.mediaDescriptions().getFirst().rtpMaps().getFirst().codec()).isEqualTo("H264");
    }

    @Test
    void testTestMediaSourceIsLive() {
        var source = new StreamingServerDemo.TestMediaSource();
        assertThat(source.isLive()).isTrue();
        assertThat(source.duration()).isEmpty();
    }

    @Test
    void testTestMediaSourceNoRecord() {
        var source = new StreamingServerDemo.TestMediaSource();
        assertThat(source.supportsRecord()).isFalse();
    }

    @Test
    void testServerHandlesFullSequence() {
        var server = StreamingServerDemo.createServer(8554);
        try {
            // OPTIONS
            var options = server.handleRequest(
                    RtspRequest.builder(RtspMethod.OPTIONS, "rtsp://localhost:8554/test/stream")
                            .cseq(1).build());
            assertThat(options.status()).isEqualTo(RtspStatus.OK);

            // DESCRIBE
            var describe = server.handleRequest(
                    RtspRequest.builder(RtspMethod.DESCRIBE, "rtsp://localhost:8554/test/stream")
                            .cseq(2).accept("application/sdp").build());
            assertThat(describe.status()).isEqualTo(RtspStatus.OK);

            // SETUP
            var setup = server.handleRequest(
                    RtspRequest.builder(RtspMethod.SETUP, "rtsp://localhost:8554/test/stream")
                            .cseq(3).transport("RTP/AVP;unicast;client_port=8000-8001").build());
            assertThat(setup.status()).isEqualTo(RtspStatus.OK);
            String sessionId = setup.headers().sessionId().get();

            // PLAY
            var play = server.handleRequest(
                    RtspRequest.builder(RtspMethod.PLAY, "rtsp://localhost:8554/test/stream")
                            .cseq(4).session(sessionId).range("npt=0-").build());
            assertThat(play.status()).isEqualTo(RtspStatus.OK);

            // TEARDOWN
            var teardown = server.handleRequest(
                    RtspRequest.builder(RtspMethod.TEARDOWN, "rtsp://localhost:8554/test/stream")
                            .cseq(5).session(sessionId).build());
            assertThat(teardown.status()).isEqualTo(RtspStatus.OK);
        } finally {
            server.close();
        }
    }
}
