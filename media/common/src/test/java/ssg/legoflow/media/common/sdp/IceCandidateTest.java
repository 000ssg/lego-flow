package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class IceCandidateTest {

    @Test
    void testParseHostCandidate() {
        IceCandidate c = IceCandidate.parse(
                "1 1 udp 2130706431 10.0.1.1 8998 typ host");

        assertThat(c.foundation()).isEqualTo("1");
        assertThat(c.componentId()).isEqualTo(1);
        assertThat(c.transport()).isEqualTo("udp");
        assertThat(c.priority()).isEqualTo(2130706431L);
        assertThat(c.address()).isEqualTo("10.0.1.1");
        assertThat(c.port()).isEqualTo(8998);
        assertThat(c.type()).isEqualTo("host");
        assertThat(c.relAddr()).isEmpty();
        assertThat(c.relPort()).isEmpty();
    }

    @Test
    void testParseReflexiveCandidate() {
        IceCandidate c = IceCandidate.parse(
                "2 1 udp 1694498815 192.0.2.3 45664 typ srflx raddr 10.0.1.1 rport 8998");

        assertThat(c.type()).isEqualTo("srflx");
        assertThat(c.relAddr()).hasValue("10.0.1.1");
        assertThat(c.relPort()).hasValue(8998);
    }

    @Test
    void testParseRelayCandidate() {
        IceCandidate c = IceCandidate.parse(
                "3 1 udp 100 203.0.113.5 9000 typ relay raddr 192.0.2.3 rport 45664");

        assertThat(c.type()).isEqualTo("relay");
        assertThat(c.address()).isEqualTo("203.0.113.5");
        assertThat(c.relAddr()).hasValue("192.0.2.3");
    }

    @Test
    void testFormatHostCandidate() {
        IceCandidate c = IceCandidate.parse("1 1 udp 2130706431 10.0.1.1 8998 typ host");

        assertThat(c.format()).isEqualTo("1 1 udp 2130706431 10.0.1.1 8998 typ host");
    }

    @Test
    void testFormatReflexiveCandidate() {
        IceCandidate c = IceCandidate.parse(
                "2 1 udp 1694498815 192.0.2.3 45664 typ srflx raddr 10.0.1.1 rport 8998");

        String formatted = c.format();
        assertThat(formatted).contains("typ srflx");
        assertThat(formatted).contains("raddr 10.0.1.1");
        assertThat(formatted).contains("rport 8998");
    }

    @Test
    void testParseRtcpComponent() {
        IceCandidate c = IceCandidate.parse(
                "1 2 udp 2130706430 10.0.1.1 8999 typ host");

        assertThat(c.componentId()).isEqualTo(2);
    }

    @Test
    void testParseTooFewFields() {
        assertThatThrownBy(() -> IceCandidate.parse("1 1 udp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        IceCandidate c = IceCandidate.parse("1 1 udp 100 10.0.0.1 5000 typ host");

        assertThat(c.toString()).startsWith("a=candidate:");
    }
}
