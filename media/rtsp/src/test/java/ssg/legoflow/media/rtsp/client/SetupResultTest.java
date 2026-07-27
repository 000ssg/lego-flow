package ssg.legoflow.media.rtsp.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.rtsp.protocol.TransportHeader;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SetupResult}.
 */
class SetupResultTest {

    @Test
    void testCreateResult() {
        var transport = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        var result = new SetupResult("abc123", 60, transport);
        assertThat(result.sessionId()).isEqualTo("abc123");
        assertThat(result.timeout()).isEqualTo(60);
        assertThat(result.transport()).isSameAs(transport);
    }

    @Test
    void testNullSessionIdThrows() {
        var transport = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        assertThatThrownBy(() -> new SetupResult(null, 60, transport))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testZeroTimeoutThrows() {
        var transport = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        assertThatThrownBy(() -> new SetupResult("abc", 0, transport))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullTransportThrows() {
        assertThatThrownBy(() -> new SetupResult("abc", 60, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testToString() {
        var transport = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        var result = new SetupResult("abc123", 60, transport);
        assertThat(result.toString()).contains("abc123").contains("60");
    }
}
