package ssg.legoflow.media.rtsp.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.rtsp.protocol.TransportHeader;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtspSession}.
 */
class RtspSessionTest {

    @Test
    void testCreateSession() {
        var session = RtspSession.create();
        assertThat(session.sessionId()).isNotBlank();
        assertThat(session.timeout()).isEqualTo(RtspSession.DEFAULT_TIMEOUT);
        assertThat(session.isTerminated()).isFalse();
        assertThat(session.isExpired()).isFalse();
    }

    @Test
    void testCreateWithTimeout() {
        var session = RtspSession.create(30);
        assertThat(session.timeout()).isEqualTo(30);
    }

    @Test
    void testGetOrCreateController() {
        var session = RtspSession.create();
        var controller = session.controller("/media/track1");
        assertThat(controller).isNotNull();
        assertThat(controller.state()).isEqualTo(StreamState.INIT);

        // Same path returns same controller
        var same = session.controller("/media/track1");
        assertThat(same).isSameAs(controller);
    }

    @Test
    void testFindController() {
        var session = RtspSession.create();
        assertThat(session.findController("/media/track1")).isEmpty();
        session.controller("/media/track1");
        assertThat(session.findController("/media/track1")).isPresent();
    }

    @Test
    void testStreamCount() {
        var session = RtspSession.create();
        assertThat(session.streamCount()).isEqualTo(0);
        session.controller("/track1");
        session.controller("/track2");
        assertThat(session.streamCount()).isEqualTo(2);
    }

    @Test
    void testSetAndGetTransport() {
        var session = RtspSession.create();
        var transport = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        session.setTransport("/track1", transport);
        assertThat(session.transport("/track1")).isPresent();
        assertThat(session.transport("/track2")).isEmpty();
    }

    @Test
    void testTerminate() {
        var session = RtspSession.create();
        var controller = session.controller("/track1");
        controller.setup();
        controller.play(0);

        session.terminate();
        assertThat(session.isTerminated()).isTrue();
        assertThat(controller.state()).isEqualTo(StreamState.TEARDOWN);
    }

    @Test
    void testTouch() {
        var session = RtspSession.create();
        session.touch();
        assertThat(session.isExpired()).isFalse();
    }

    @Test
    void testSessionIdIsUnique() {
        var s1 = RtspSession.create();
        var s2 = RtspSession.create();
        assertThat(s1.sessionId()).isNotEqualTo(s2.sessionId());
    }

    @Test
    void testToString() {
        var session = RtspSession.create();
        assertThat(session.toString()).contains("RtspSession");
    }
}
