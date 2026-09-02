package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class OriginTest {

    @Test
    void testParseStandardOrigin() {
        Origin o = Origin.parse("alice 2890844526 2890842807 IN IP4 10.0.0.1");

        assertThat(o.username()).isEqualTo("alice");
        assertThat(o.sessionId()).isEqualTo(2890844526L);
        assertThat(o.version()).isEqualTo(2890842807L);
        assertThat(o.netType()).isEqualTo("IN");
        assertThat(o.addrType()).isEqualTo("IP4");
        assertThat(o.address()).isEqualTo("10.0.0.1");
    }

    @Test
    void testParseDashUsername() {
        Origin o = Origin.parse("- 123456 1 IN IP4 127.0.0.1");

        assertThat(o.username()).isEqualTo("-");
        assertThat(o.sessionId()).isEqualTo(123456L);
    }

    @Test
    void testParseIpv6() {
        Origin o = Origin.parse("bob 987654 2 IN IP6 ::1");

        assertThat(o.addrType()).isEqualTo("IP6");
        assertThat(o.address()).isEqualTo("::1");
    }

    @Test
    void testFormat() {
        Origin o = new Origin("alice", 2890844526L, 2890842807L, "IN", "IP4", "10.0.0.1");

        assertThat(o.format()).isEqualTo("alice 2890844526 2890842807 IN IP4 10.0.0.1");
    }

    @Test
    void testRoundTrip() {
        String line = "charlie 111222333 444555666 IN IP4 192.168.1.100";
        Origin o = Origin.parse(line);

        assertThat(o.format()).isEqualTo(line);
    }

    @Test
    void testParseInvalidTooFewFields() {
        assertThatThrownBy(() -> Origin.parse("alice 123 456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 6 fields");
    }

    @Test
    void testToString() {
        Origin o = new Origin("-", 1L, 1L, "IN", "IP4", "0.0.0.0");

        assertThat(o.toString()).isEqualTo("o=- 1 1 IN IP4 0.0.0.0");
    }

    @Test
    void testNullUsername() {
        assertThatThrownBy(() -> new Origin(null, 1L, 1L, "IN", "IP4", "0.0.0.0"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testLargeSessionId() {
        Origin o = Origin.parse("- 9999999999999 1 IN IP4 10.0.0.1");

        assertThat(o.sessionId()).isEqualTo(9999999999999L);
    }
}
