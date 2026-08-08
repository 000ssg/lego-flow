package ssg.legoflow.ssh.connection;

import ssg.legoflow.ssh.transport.SshTransport;

/**
 * Forwarded TCP/IP channel for remote port forwarding per RFC 4254 section 7.1.
 *
 * @since 0.1.0
 */
public final class ForwardedTcpIpChannel extends SshChannel {

    private final String connectedAddress;
    private final int connectedPort;
    private final String originatorAddress;
    private final int originatorPort;

    /**
     * Creates a new forwarded-tcpip channel.
     *
     * @param localId            the local channel ID
     * @param transport          the transport layer
     * @param connectedAddress   the address the connection was received on
     * @param connectedPort      the port the connection was received on
     * @param originatorAddress  the originator's IP address
     * @param originatorPort     the originator's port
     */
    public ForwardedTcpIpChannel(int localId, SshTransport transport,
                                  String connectedAddress, int connectedPort,
                                  String originatorAddress, int originatorPort) {
        super(localId, transport);
        this.connectedAddress = connectedAddress;
        this.connectedPort = connectedPort;
        this.originatorAddress = originatorAddress;
        this.originatorPort = originatorPort;
    }

    @Override
    public String channelType() { return "forwarded-tcpip"; }

    /** @return the connected address */
    public String connectedAddress() { return connectedAddress; }
    /** @return the connected port */
    public int connectedPort() { return connectedPort; }
    /** @return the originator address */
    public String originatorAddress() { return originatorAddress; }
    /** @return the originator port */
    public int originatorPort() { return originatorPort; }
}
