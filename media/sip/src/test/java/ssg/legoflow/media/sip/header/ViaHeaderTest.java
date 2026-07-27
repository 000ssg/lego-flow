package ssg.legoflow.media.sip.header;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ViaHeader}.
 */
class ViaHeaderTest {

    @Test
    void testParseBasicVia() {
        var via = ViaHeader.parse("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776asdhds");
        assertThat(via.protocol()).isEqualTo("SIP/2.0");
        assertThat(via.transport()).isEqualTo("UDP");
        assertThat(via.host()).isEqualTo("192.168.1.1");
        assertThat(via.port()).isEqualTo(5060);
        assertThat(via.branch()).isEqualTo("z9hG4bK776asdhds");
    }

    @Test
    void testParseTcpVia() {
        var via = ViaHeader.parse("SIP/2.0/TCP proxy.example.com:5060;branch=z9hG4bKtest");
        assertThat(via.transport()).isEqualTo("TCP");
        assertThat(via.host()).isEqualTo("proxy.example.com");
    }

    @Test
    void testParseViaWithReceived() {
        var via = ViaHeader.parse(
                "SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776;received=10.0.0.1");
        assertThat(via.received()).hasValue("10.0.0.1");
    }

    @Test
    void testParseViaWithRport() {
        var via = ViaHeader.parse(
                "SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776;rport=5070");
        assertThat(via.rport()).hasValue(5070);
    }

    @Test
    void testParseViaWithoutPort() {
        var via = ViaHeader.parse("SIP/2.0/UDP proxy.example.com;branch=z9hG4bK776");
        assertThat(via.host()).isEqualTo("proxy.example.com");
        assertThat(via.port()).isEqualTo(-1);
    }

    @Test
    void testFormatVia() {
        var via = ViaHeader.parse("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776");
        String formatted = via.format();
        assertThat(formatted).contains("SIP/2.0/UDP");
        assertThat(formatted).contains("192.168.1.1:5060");
        assertThat(formatted).contains("branch=z9hG4bK776");
    }

    @Test
    void testBranchMagicCookie() {
        assertThat(ViaHeader.BRANCH_MAGIC_COOKIE).isEqualTo("z9hG4bK");
    }

    @Test
    void testInvalidViaThrows() {
        assertThatThrownBy(() -> ViaHeader.parse("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullViaThrows() {
        assertThatThrownBy(() -> ViaHeader.parse(null))
                .isInstanceOf(NullPointerException.class);
    }
}
