package ssg.legoflow.ssh.auth;

import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.nio.ByteBuffer;

/**
 * None authentication method for discovering allowed methods per RFC 4252.
 *
 * @since 1.0.0
 */
public final class NoneAuth implements AuthMethod {

    @Override public String methodName() { return "none"; }
    @Override public boolean isInteractive() { return false; }

    @Override
    public byte[] encodeRequest(String username, String serviceName) {
        ByteBuffer buf = ByteBuffer.allocate(256);
        buf.put((byte) 50); // SSH_MSG_USERAUTH_REQUEST
        SshTransportCodec.writeString(buf, username);
        SshTransportCodec.writeString(buf, serviceName);
        SshTransportCodec.writeString(buf, "none");
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
