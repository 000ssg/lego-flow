package ssg.legoflow.ssh.connection;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class X11ForwardingChannelTest {

    @Test
    void testChannelTypeIsX11() {
        X11ForwardingChannel channel = new X11ForwardingChannel(0, null, "127.0.0.1", 6010);
        assertThat(channel.channelType()).isEqualTo("x11");
    }

    @Test
    void testOriginatorAddressStored() {
        X11ForwardingChannel channel = new X11ForwardingChannel(0, null, "192.168.1.1", 6010);
        assertThat(channel.originatorAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    void testOriginatorPortStored() {
        X11ForwardingChannel channel = new X11ForwardingChannel(0, null, "127.0.0.1", 6042);
        assertThat(channel.originatorPort()).isEqualTo(6042);
    }

    @Test
    void testLocalId() {
        X11ForwardingChannel channel = new X11ForwardingChannel(42, null, "10.0.0.1", 6000);
        assertThat(channel.localId()).isEqualTo(42);
    }

    @Test
    void testChannelRequestConstants() {
        assertThat(ChannelRequest.X11_REQ).isEqualTo("x11-req");
        assertThat(ChannelRequest.X11_FORWARDING).isEqualTo("x11");
    }

    @Test
    void testAuthAgentReqConstant() {
        assertThat(ChannelRequest.AUTH_AGENT_REQ).isEqualTo("auth-agent-req@openssh.com");
    }
}
