package ssg.legoflow.media.rtsp.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.media.rtsp.fixture.StreamingServerDemo;
import ssg.legoflow.media.rtsp.protocol.RtspStatus;
import ssg.legoflow.media.rtsp.server.RtspServer;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtspClient}.
 */
class RtspClientTest {

    private RtspServer server;
    private RtspClient client;

    @BeforeEach
    void setUp() {
        server = StreamingServerDemo.createServer(8554);
        client = new RtspClient("rtsp://localhost:8554/test/stream", server);
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    @Test
    void testOptions() {
        var response = client.options();
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(response.headers().first("Public")).isPresent();
    }

    @Test
    void testDescribe() {
        var response = client.describe();
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(response.hasBody()).isTrue();
    }

    @Test
    void testSetup() {
        var result = client.setup("RTP/AVP;unicast;client_port=8000-8001");
        assertThat(result.sessionId()).isNotBlank();
        assertThat(result.timeout()).isGreaterThan(0);
        assertThat(result.transport()).isNotNull();
    }

    @Test
    void testPlay() {
        client.setup("RTP/AVP;unicast;client_port=8000-8001");
        var response = client.play();
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
    }

    @Test
    void testPlayWithRange() {
        client.setup("RTP/AVP;unicast;client_port=8000-8001");
        var response = client.play("npt=10-");
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
    }

    @Test
    void testPause() {
        client.setup("RTP/AVP;unicast;client_port=8000-8001");
        client.play();
        var response = client.pause();
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
    }

    @Test
    void testTeardown() {
        client.setup("RTP/AVP;unicast;client_port=8000-8001");
        var response = client.teardown();
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(client.session().get().isActive()).isFalse();
    }

    @Test
    void testGetParameter() {
        client.setup("RTP/AVP;unicast;client_port=8000-8001");
        var response = client.getParameter();
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
    }

    @Test
    void testCseqIncrementing() {
        assertThat(client.currentCseq()).isEqualTo(0);
        client.options();
        assertThat(client.currentCseq()).isEqualTo(1);
        client.describe();
        assertThat(client.currentCseq()).isEqualTo(2);
    }

    @Test
    void testPlayWithoutSetupThrows() {
        assertThatThrownBy(client::play)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("session");
    }

    @Test
    void testPauseWithoutSetupThrows() {
        assertThatThrownBy(client::pause)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testFullWorkflow() {
        // OPTIONS -> DESCRIBE -> SETUP -> PLAY -> PAUSE -> PLAY -> TEARDOWN
        assertThat(client.options().isSuccess()).isTrue();
        assertThat(client.describe().isSuccess()).isTrue();
        var setup = client.setup("RTP/AVP;unicast;client_port=8000-8001");
        assertThat(setup.sessionId()).isNotBlank();
        assertThat(client.play().isSuccess()).isTrue();
        assertThat(client.pause().isSuccess()).isTrue();
        assertThat(client.play("npt=5-").isSuccess()).isTrue();
        assertThat(client.teardown().isSuccess()).isTrue();
    }

    @Test
    void testSessionTracking() {
        assertThat(client.session()).isEmpty();
        client.setup("RTP/AVP;unicast;client_port=8000-8001");
        assertThat(client.session()).isPresent();
        assertThat(client.session().get().isActive()).isTrue();
    }

    @Test
    void testInterleavedSetup() {
        var result = client.setup("RTP/AVP/TCP;unicast;interleaved=0-1");
        assertThat(result.transport().isInterleaved()).isTrue();
    }

    @Test
    void testToString() {
        assertThat(client.toString()).contains("RtspClient");
    }
}
