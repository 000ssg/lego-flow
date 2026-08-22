package ssg.legoflow.media.rtsp.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.rtsp.protocol.TransportHeader;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtspClientSession}.
 */
class RtspClientSessionTest {

    @Test
    void testCreateFromSetupResult() {
        var transport = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        var result = new SetupResult("session1", 60, transport);
        var session = new RtspClientSession(result);

        assertThat(session.sessionId()).isEqualTo("session1");
        assertThat(session.timeout()).isEqualTo(60);
        assertThat(session.transport()).isNotNull();
        assertThat(session.isActive()).isTrue();
    }

    @Test
    void testCreateDirect() {
        var session = new RtspClientSession("session2", 30);
        assertThat(session.sessionId()).isEqualTo("session2");
        assertThat(session.timeout()).isEqualTo(30);
        assertThat(session.isActive()).isTrue();
    }

    @Test
    void testTerminate() {
        var session = new RtspClientSession("s1", 60);
        session.terminate();
        assertThat(session.isActive()).isFalse();
    }

    @Test
    void testNeedsKeepAlive() {
        var session = new RtspClientSession("s1", 60);
        // Freshly created session should not need keep-alive
        assertThat(session.needsKeepAlive()).isFalse();
    }

    @Test
    void testMarkKeepAlive() {
        var session = new RtspClientSession("s1", 60);
        session.markKeepAlive();
        assertThat(session.needsKeepAlive()).isFalse();
    }

    @Test
    void testTerminatedSessionNoKeepAlive() {
        var session = new RtspClientSession("s1", 60);
        session.terminate();
        assertThat(session.needsKeepAlive()).isFalse();
    }

    @Test
    void testSetTransport() {
        var session = new RtspClientSession("s1", 60);
        assertThat(session.transport()).isNull();
        var transport = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        session.setTransport(transport);
        assertThat(session.transport()).isNotNull();
    }

    @Test
    void testToString() {
        var session = new RtspClientSession("s1", 60);
        assertThat(session.toString()).contains("s1").contains("active=true");
    }
}
