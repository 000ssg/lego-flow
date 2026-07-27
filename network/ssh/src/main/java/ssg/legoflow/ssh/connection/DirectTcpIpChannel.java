package ssg.legoflow.ssh.connection;

import ssg.legoflow.ssh.transport.SshTransport;

/**
 * Direct TCP/IP channel for local port forwarding per RFC 4254 section 7.2.
 *
 * @since 1.0.0
 */
public final class DirectTcpIpChannel extends SshChannel {

    private final String targetHost;
    private final int targetPort;
    private final String originatorAddress;
    private final int originatorPort;

    /**
     * Creates a new direct-tcpip channel.
     *
     * @param localId           the local channel ID
     * @param transport         the transport layer
     * @param targetHost        the target host to connect to
     * @param targetPort        the target port
     * @param originatorAddress the originator's IP address
     * @param originatorPort    the originator's port
     */
    public DirectTcpIpChannel(int localId, SshTransport transport,
                               String targetHost, int targetPort,
                               String originatorAddress, int originatorPort) {
        super(localId, transport);
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.originatorAddress = originatorAddress;
        this.originatorPort = originatorPort;
    }

    @Override
    public String channelType() { return "direct-tcpip"; }

    /** @return the target host */
    public String targetHost() { return targetHost; }
    /** @return the target port */
    public int targetPort() { return targetPort; }
    /** @return the originator address */
    public String originatorAddress() { return originatorAddress; }
    /** @return the originator port */
    public int originatorPort() { return originatorPort; }
}
