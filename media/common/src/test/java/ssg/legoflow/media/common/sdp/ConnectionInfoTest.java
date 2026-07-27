package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConnectionInfoTest {

    @Test
    void testParseUnicast() {
        ConnectionInfo ci = ConnectionInfo.parse("IN IP4 10.0.0.1");

        assertThat(ci.netType()).isEqualTo("IN");
        assertThat(ci.addrType()).isEqualTo("IP4");
        assertThat(ci.address()).isEqualTo("10.0.0.1");
        assertThat(ci.ttl()).isEmpty();
        assertThat(ci.count()).isEmpty();
    }

    @Test
    void testParseMulticastWithTtl() {
        ConnectionInfo ci = ConnectionInfo.parse("IN IP4 224.2.36.42/127");

        assertThat(ci.address()).isEqualTo("224.2.36.42");
        assertThat(ci.ttl()).hasValue(127);
        assertThat(ci.count()).isEmpty();
    }

    @Test
    void testParseMulticastWithTtlAndCount() {
        ConnectionInfo ci = ConnectionInfo.parse("IN IP4 224.2.1.1/127/3");

        assertThat(ci.address()).isEqualTo("224.2.1.1");
        assertThat(ci.ttl()).hasValue(127);
        assertThat(ci.count()).hasValue(3);
    }

    @Test
    void testParseIpv6() {
        ConnectionInfo ci = ConnectionInfo.parse("IN IP6 ::1");

        assertThat(ci.addrType()).isEqualTo("IP6");
        assertThat(ci.address()).isEqualTo("::1");
    }

    @Test
    void testFormatUnicast() {
        ConnectionInfo ci = ConnectionInfo.unicast("IN", "IP4", "192.168.1.1");

        assertThat(ci.format()).isEqualTo("IN IP4 192.168.1.1");
    }

    @Test
    void testFormatMulticastTtl() {
        ConnectionInfo ci = ConnectionInfo.multicast("IN", "IP4", "224.2.36.42", 127);

        assertThat(ci.format()).isEqualTo("IN IP4 224.2.36.42/127");
    }

    @Test
    void testFormatMulticastTtlCount() {
        ConnectionInfo ci = ConnectionInfo.multicast("IN", "IP4", "224.2.1.1", 127, 3);

        assertThat(ci.format()).isEqualTo("IN IP4 224.2.1.1/127/3");
    }

    @Test
    void testRoundTrip() {
        String line = "IN IP4 224.2.1.1/127/3";
        ConnectionInfo ci = ConnectionInfo.parse(line);

        assertThat(ci.format()).isEqualTo(line);
    }

    @Test
    void testParseInvalid() {
        assertThatThrownBy(() -> ConnectionInfo.parse("IN IP4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 3 fields");
    }

    @Test
    void testToString() {
        ConnectionInfo ci = ConnectionInfo.unicast("IN", "IP4", "10.0.0.1");

        assertThat(ci.toString()).isEqualTo("c=IN IP4 10.0.0.1");
    }
}
