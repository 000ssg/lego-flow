package ssg.legoflow.ssh.connection;

import ssg.legoflow.ssh.transport.SshTransport;

/**
 * X11 forwarding channel per RFC 4254 section 6.3.1.
 *
 * <p>Carries X11 protocol data between the client and the X display server.
 * Created when the remote server opens a channel of type {@code "x11"} after
 * X11 forwarding has been requested on a session channel.
 *
 * @since 1.0.0
 */
public final class X11ForwardingChannel extends SshChannel {

    private final String originatorAddress;
    private final int originatorPort;

    /**
     * Creates a new X11 forwarding channel.
     *
     * @param localId            the local channel ID
     * @param transport          the transport layer
     * @param originatorAddress  the originator's IP address
     * @param originatorPort     the originator's port number
     */
    public X11ForwardingChannel(int localId, SshTransport transport,
                                 String originatorAddress, int originatorPort) {
        super(localId, transport);
        this.originatorAddress = originatorAddress;
        this.originatorPort = originatorPort;
    }

    @Override
    public String channelType() { return "x11"; }

    /**
     * Returns the originator address of the X11 connection.
     *
     * @return the originator IP address
     */
    public String originatorAddress() { return originatorAddress; }

    /**
     * Returns the originator port of the X11 connection.
     *
     * @return the originator port number
     */
    public int originatorPort() { return originatorPort; }
}
