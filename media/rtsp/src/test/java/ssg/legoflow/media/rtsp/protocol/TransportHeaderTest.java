package ssg.legoflow.media.rtsp.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link TransportHeader}.
 */
class TransportHeaderTest {

    @Test
    void testParseUdpUnicast() {
        var t = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        assertThat(t.protocol()).isEqualTo(TransportHeader.Protocol.RTP_AVP_UDP);
        assertThat(t.castMode()).isEqualTo(TransportHeader.CastMode.UNICAST);
        assertThat(t.clientPortRtp()).hasValue(8000);
        assertThat(t.clientPortRtcp()).hasValue(8001);
        assertThat(t.isInterleaved()).isFalse();
    }

    @Test
    void testParseTcpInterleaved() {
        var t = TransportHeader.parse("RTP/AVP/TCP;unicast;interleaved=0-1");
        assertThat(t.protocol()).isEqualTo(TransportHeader.Protocol.RTP_AVP_TCP);
        assertThat(t.castMode()).isEqualTo(TransportHeader.CastMode.UNICAST);
        assertThat(t.isInterleaved()).isTrue();
        assertThat(t.interleavedRtp()).hasValue(0);
        assertThat(t.interleavedRtcp()).hasValue(1);
    }

    @Test
    void testParseMulticast() {
        var t = TransportHeader.parse("RTP/AVP;multicast;destination=239.0.0.1");
        assertThat(t.castMode()).isEqualTo(TransportHeader.CastMode.MULTICAST);
        assertThat(t.destination()).hasValue("239.0.0.1");
    }

    @Test
    void testParseWithServerPorts() {
        var t = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001;server_port=6000-6001");
        assertThat(t.clientPortRtp()).hasValue(8000);
        assertThat(t.clientPortRtcp()).hasValue(8001);
        assertThat(t.serverPortRtp()).hasValue(6000);
        assertThat(t.serverPortRtcp()).hasValue(6001);
    }

    @Test
    void testParseWithSsrc() {
        var t = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001;ssrc=1A2B3C4D");
        assertThat(t.ssrc()).hasValue("1A2B3C4D");
    }

    @Test
    void testParseWithSource() {
        var t = TransportHeader.parse("RTP/AVP;unicast;source=192.168.1.1;client_port=8000-8001");
        assertThat(t.source()).hasValue("192.168.1.1");
    }

    @Test
    void testFormatUdpUnicast() {
        var t = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        String formatted = t.format();
        assertThat(formatted).contains("RTP/AVP");
        assertThat(formatted).contains("unicast");
        assertThat(formatted).contains("client_port=8000-8001");
    }

    @Test
    void testFormatTcpInterleaved() {
        var t = TransportHeader.parse("RTP/AVP/TCP;unicast;interleaved=0-1");
        String formatted = t.format();
        assertThat(formatted).contains("RTP/AVP/TCP");
        assertThat(formatted).contains("interleaved=0-1");
    }

    @Test
    void testRoundTrip() {
        String original = "RTP/AVP;unicast;destination=192.168.1.100;source=10.0.0.1;client_port=8000-8001;server_port=6000-6001;ssrc=AABB";
        var t = TransportHeader.parse(original);
        String formatted = t.format();
        var t2 = TransportHeader.parse(formatted);
        assertThat(t2.protocol()).isEqualTo(t.protocol());
        assertThat(t2.castMode()).isEqualTo(t.castMode());
        assertThat(t2.clientPortRtp()).isEqualTo(t.clientPortRtp());
        assertThat(t2.serverPortRtp()).isEqualTo(t.serverPortRtp());
        assertThat(t2.ssrc()).isEqualTo(t.ssrc());
    }

    @Test
    void testSingleClientPort() {
        var t = TransportHeader.parse("RTP/AVP;unicast;client_port=8000");
        assertThat(t.clientPortRtp()).hasValue(8000);
        assertThat(t.clientPortRtcp()).isEmpty();
    }

    @Test
    void testToString() {
        var t = TransportHeader.parse("RTP/AVP;unicast;client_port=8000-8001");
        assertThat(t.toString()).startsWith("Transport:");
    }
}
