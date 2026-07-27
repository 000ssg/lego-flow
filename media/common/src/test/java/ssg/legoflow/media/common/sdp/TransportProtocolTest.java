package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TransportProtocolTest {

    @Test
    void testAllProtocols() {
        assertThat(TransportProtocol.RTP_AVP.token()).isEqualTo("RTP/AVP");
        assertThat(TransportProtocol.RTP_SAVP.token()).isEqualTo("RTP/SAVP");
        assertThat(TransportProtocol.RTP_AVPF.token()).isEqualTo("RTP/AVPF");
        assertThat(TransportProtocol.RTP_SAVPF.token()).isEqualTo("RTP/SAVPF");
        assertThat(TransportProtocol.UDP.token()).isEqualTo("udp");
        assertThat(TransportProtocol.TCP.token()).isEqualTo("TCP");
        assertThat(TransportProtocol.TCP_RTP_AVP.token()).isEqualTo("TCP/RTP/AVP");
    }

    @Test
    void testFromTokenCaseInsensitive() {
        assertThat(TransportProtocol.fromToken("rtp/avp")).isEqualTo(TransportProtocol.RTP_AVP);
        assertThat(TransportProtocol.fromToken("RTP/SAVPF")).isEqualTo(TransportProtocol.RTP_SAVPF);
        assertThat(TransportProtocol.fromToken("UDP")).isEqualTo(TransportProtocol.UDP);
    }

    @Test
    void testFromTokenUnknown() {
        assertThatThrownBy(() -> TransportProtocol.fromToken("SCTP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testValues() {
        assertThat(TransportProtocol.values()).hasSize(7);
    }
}
