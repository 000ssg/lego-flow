package ssg.legoflow.ssh.connection;

import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.nio.ByteBuffer;

/**
 * SSH global requests per RFC 4254 section 4.
 *
 * @since 0.1.0
 */
public final class GlobalRequest {

    /** Request remote port forwarding. */
    public static final String TCPIP_FORWARD = "tcpip-forward";
    /** Cancel remote port forwarding. */
    public static final String CANCEL_TCPIP_FORWARD = "cancel-tcpip-forward";

    private GlobalRequest() {}

    /**
     * Encodes a tcpip-forward global request.
     *
     * @param bindAddress the address to bind (empty string for all interfaces)
     * @param bindPort    the port to bind (0 for dynamic)
     * @return the encoded request payload
     */
    public static byte[] encodeTcpIpForward(String bindAddress, int bindPort) {
        ByteBuffer buf = ByteBuffer.allocate(256);
        buf.put((byte) 80); // SSH_MSG_GLOBAL_REQUEST
        SshTransportCodec.writeString(buf, TCPIP_FORWARD);
        SshTransportCodec.writeBoolean(buf, true); // want reply
        SshTransportCodec.writeString(buf, bindAddress);
        buf.putInt(bindPort);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Encodes a cancel-tcpip-forward global request.
     *
     * @param bindAddress the address that was bound
     * @param bindPort    the port that was bound
     * @return the encoded request payload
     */
    public static byte[] encodeCancelTcpIpForward(String bindAddress, int bindPort) {
        ByteBuffer buf = ByteBuffer.allocate(256);
        buf.put((byte) 80);
        SshTransportCodec.writeString(buf, CANCEL_TCPIP_FORWARD);
        SshTransportCodec.writeBoolean(buf, true);
        SshTransportCodec.writeString(buf, bindAddress);
        buf.putInt(bindPort);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
