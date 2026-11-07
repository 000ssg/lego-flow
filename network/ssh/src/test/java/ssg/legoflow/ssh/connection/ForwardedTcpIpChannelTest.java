package ssg.legoflow.ssh.connection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ForwardedTcpIpChannel}.
 */
class ForwardedTcpIpChannelTest {

    @Test
    void channelType() {
        ForwardedTcpIpChannel channel = new ForwardedTcpIpChannel(
            1, null, "0.0.0.0", 8080, "127.0.0.1", 12345);

        assertEquals("forwarded-tcpip", channel.channelType());
    }

    @Test
    void connectedAddress() {
        ForwardedTcpIpChannel channel = new ForwardedTcpIpChannel(
            1, null, "192.168.1.100", 22, "127.0.0.1", 54321);

        assertEquals("192.168.1.100", channel.connectedAddress());
    }

    @Test
    void connectedPort() {
        ForwardedTcpIpChannel channel = new ForwardedTcpIpChannel(
            1, null, "0.0.0.0", 2222, "127.0.0.1", 12345);

        assertEquals(2222, channel.connectedPort());
    }

    @Test
    void originatorAddress() {
        ForwardedTcpIpChannel channel = new ForwardedTcpIpChannel(
            1, null, "0.0.0.0", 22, "10.0.0.1", 54321);

        assertEquals("10.0.0.1", channel.originatorAddress());
    }

    @Test
    void originatorPort() {
        ForwardedTcpIpChannel channel = new ForwardedTcpIpChannel(
            1, null, "0.0.0.0", 22, "127.0.0.1", 54321);

        assertEquals(54321, channel.originatorPort());
    }

    @Test
    void localId() {
        ForwardedTcpIpChannel channel = new ForwardedTcpIpChannel(
            99, null, "0.0.0.0", 80, "127.0.0.1", 1234);

        assertEquals(99, channel.localId());
    }
}
