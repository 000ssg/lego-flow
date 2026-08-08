package ssg.legoflow.media.rtsp.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.media.rtsp.fixture.StreamingServerDemo;
import ssg.legoflow.media.rtsp.protocol.*;

import java.net.URI;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RtspServer}.
 */
class RtspServerTest {

    private RtspServer server;

    @BeforeEach
    void setUp() {
        server = StreamingServerDemo.createServer(8554);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void testOptions() {
        var request = RtspRequest.builder(RtspMethod.OPTIONS, "rtsp://localhost:8554/test/stream")
                .cseq(1).build();
        var response = server.handleRequest(request);

        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(response.headers().first(RtspHeaders.PUBLIC)).isPresent();
        assertThat(response.headers().first(RtspHeaders.PUBLIC).get())
                .contains("OPTIONS").contains("DESCRIBE").contains("SETUP");
    }

    @Test
    void testDescribe() {
        var request = RtspRequest.builder(RtspMethod.DESCRIBE, "rtsp://localhost:8554/test/stream")
                .cseq(2)
                .accept("application/sdp")
                .build();
        var response = server.handleRequest(request);

        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(response.headers().first(RtspHeaders.CONTENT_TYPE))
                .hasValue("application/sdp");
        assertThat(response.hasBody()).isTrue();
    }

    @Test
    void testDescribeNotFound() {
        var request = RtspRequest.builder(RtspMethod.DESCRIBE, "rtsp://localhost:8554/nonexistent")
                .cseq(2)
                .accept("application/sdp")
                .build();
        var response = server.handleRequest(request);
        assertThat(response.status()).isEqualTo(RtspStatus.NOT_FOUND);
    }

    @Test
    void testSetup() {
        var request = RtspRequest.builder(RtspMethod.SETUP, "rtsp://localhost:8554/test/stream")
                .cseq(3)
                .transport("RTP/AVP;unicast;client_port=8000-8001")
                .build();
        var response = server.handleRequest(request);

        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(response.headers().sessionId()).isPresent();
        assertThat(response.headers().first(RtspHeaders.TRANSPORT)).isPresent();
    }

    @Test
    void testSetupNoTransportHeader() {
        var request = RtspRequest.builder(RtspMethod.SETUP, "rtsp://localhost:8554/test/stream")
                .cseq(3)
                .build();
        var response = server.handleRequest(request);
        assertThat(response.status()).isEqualTo(RtspStatus.BAD_REQUEST);
    }

    @Test
    void testPlayAfterSetup() {
        // Setup first
        var setup = RtspRequest.builder(RtspMethod.SETUP, "rtsp://localhost:8554/test/stream")
                .cseq(3)
                .transport("RTP/AVP;unicast;client_port=8000-8001")
                .build();
        var setupResp = server.handleRequest(setup);
        String sessionId = setupResp.headers().sessionId().get();

        // Play
        var play = RtspRequest.builder(RtspMethod.PLAY, "rtsp://localhost:8554/test/stream")
                .cseq(4)
                .session(sessionId)
                .range("npt=0-")
                .build();
        var playResp = server.handleRequest(play);
        assertThat(playResp.status()).isEqualTo(RtspStatus.OK);
    }

    @Test
    void testPlayWithoutSessionReturns454() {
        var play = RtspRequest.builder(RtspMethod.PLAY, "rtsp://localhost:8554/test/stream")
                .cseq(4)
                .build();
        var response = server.handleRequest(play);
        assertThat(response.status()).isEqualTo(RtspStatus.SESSION_NOT_FOUND);
    }

    @Test
    void testPauseAfterPlay() {
        // Setup + Play
        var setup = RtspRequest.builder(RtspMethod.SETUP, "rtsp://localhost:8554/test/stream")
                .cseq(3)
                .transport("RTP/AVP;unicast;client_port=8000-8001")
                .build();
        String sessionId = server.handleRequest(setup).headers().sessionId().get();

        server.handleRequest(RtspRequest.builder(RtspMethod.PLAY, "rtsp://localhost:8554/test/stream")
                .cseq(4).session(sessionId).range("npt=0-").build());

        // Pause
        var pause = RtspRequest.builder(RtspMethod.PAUSE, "rtsp://localhost:8554/test/stream")
                .cseq(5).session(sessionId).build();
        var response = server.handleRequest(pause);
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
    }

    @Test
    void testTeardown() {
        var setup = RtspRequest.builder(RtspMethod.SETUP, "rtsp://localhost:8554/test/stream")
                .cseq(3)
                .transport("RTP/AVP;unicast;client_port=8000-8001")
                .build();
        String sessionId = server.handleRequest(setup).headers().sessionId().get();
        assertThat(server.sessionCount()).isEqualTo(1);

        var teardown = RtspRequest.builder(RtspMethod.TEARDOWN, "rtsp://localhost:8554/test/stream")
                .cseq(6).session(sessionId).build();
        var response = server.handleRequest(teardown);
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(server.sessionCount()).isEqualTo(0);
    }

    @Test
    void testGetParameterKeepAlive() {
        var setup = RtspRequest.builder(RtspMethod.SETUP, "rtsp://localhost:8554/test/stream")
                .cseq(3)
                .transport("RTP/AVP;unicast;client_port=8000-8001")
                .build();
        String sessionId = server.handleRequest(setup).headers().sessionId().get();

        var getParam = RtspRequest.builder(RtspMethod.GET_PARAMETER, "rtsp://localhost:8554/test/stream")
                .cseq(7).session(sessionId).build();
        var response = server.handleRequest(getParam);
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
    }

    @Test
    void testSetParameter() {
        var setup = RtspRequest.builder(RtspMethod.SETUP, "rtsp://localhost:8554/test/stream")
                .cseq(3)
                .transport("RTP/AVP;unicast;client_port=8000-8001")
                .build();
        String sessionId = server.handleRequest(setup).headers().sessionId().get();

        var setParam = RtspRequest.builder(RtspMethod.SET_PARAMETER, "rtsp://localhost:8554/test/stream")
                .cseq(8).session(sessionId).build();
        var response = server.handleRequest(setParam);
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
    }

    @Test
    void testAnnounce() {
        var announce = RtspRequest.builder(RtspMethod.ANNOUNCE, "rtsp://localhost:8554/test/stream")
                .cseq(9).build();
        var response = server.handleRequest(announce);
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
    }

    @Test
    void testSessionCount() {
        assertThat(server.sessionCount()).isEqualTo(0);
    }

    @Test
    void testRegisterAndUnregisterMedia() {
        server.unregisterMedia("/test/stream");
        var request = RtspRequest.builder(RtspMethod.DESCRIBE, "rtsp://localhost:8554/test/stream")
                .cseq(1).accept("application/sdp").build();
        var response = server.handleRequest(request);
        assertThat(response.status()).isEqualTo(RtspStatus.NOT_FOUND);
    }

    @Test
    void testServerPort() {
        assertThat(server.port()).isEqualTo(8554);
    }

    @Test
    void testServerName() {
        assertThat(server.serverName()).contains("LegoFlow");
    }

    @Test
    void testDescribeWrongAccept() {
        var request = RtspRequest.builder(RtspMethod.DESCRIBE, "rtsp://localhost:8554/test/stream")
                .cseq(2)
                .accept("text/html")
                .build();
        var response = server.handleRequest(request);
        assertThat(response.status()).isEqualTo(RtspStatus.NOT_ACCEPTABLE);
    }

    @Test
    void testInterleavedSetup() {
        var request = RtspRequest.builder(RtspMethod.SETUP, "rtsp://localhost:8554/test/stream")
                .cseq(3)
                .transport("RTP/AVP/TCP;unicast;interleaved=0-1")
                .build();
        var response = server.handleRequest(request);
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        var transport = TransportHeader.parse(response.headers().first(RtspHeaders.TRANSPORT).get());
        assertThat(transport.isInterleaved()).isTrue();
    }
}
