package ssg.legoflow.ssh.connection;

import org.junit.jupiter.api.Test;
import ssg.legoflow.ssh.transport.SshTransport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DirectTcpIpChannel}.
 */
class DirectTcpIpChannelTest {

    @Test
    void channelType() {
        SshTransport transport = null; // Not used in this test
        DirectTcpIpChannel channel = new DirectTcpIpChannel(
            1, transport, "localhost", 8080, "127.0.0.1", 12345);

        assertEquals("direct-tcpip", channel.channelType());
    }

    @Test
    void targetHost() {
        DirectTcpIpChannel channel = new DirectTcpIpChannel(
            1, null, "example.com", 443, "127.0.0.1", 12345);

        assertEquals("example.com", channel.targetHost());
    }

    @Test
    void targetPort() {
        DirectTcpIpChannel channel = new DirectTcpIpChannel(
            1, null, "localhost", 8080, "127.0.0.1", 12345);

        assertEquals(8080, channel.targetPort());
    }

    @Test
    void originatorAddress() {
        DirectTcpIpChannel channel = new DirectTcpIpChannel(
            1, null, "localhost", 8080, "192.168.1.1", 54321);

        assertEquals("192.168.1.1", channel.originatorAddress());
    }

    @Test
    void originatorPort() {
        DirectTcpIpChannel channel = new DirectTcpIpChannel(
            1, null, "localhost", 8080, "127.0.0.1", 54321);

        assertEquals(54321, channel.originatorPort());
    }

    @Test
    void localId() {
        DirectTcpIpChannel channel = new DirectTcpIpChannel(
            42, null, "localhost", 80, "127.0.0.1", 1234);

        assertEquals(42, channel.localId());
    }
}
