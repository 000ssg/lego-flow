package ssg.legoflow.ssh.auth;

import ssg.legoflow.ssh.transport.SshTransportCodec;
import java.nio.ByteBuffer;
import java.util.Objects;
/**
 * Password authentication method per RFC 4252 section 8.
 *
 * @since 0.1.0
 */
public final class PasswordAuth implements AuthMethod {

    private final String password;

    /**
     * Creates a password authentication method.
     *
     * @param password the password
     */
    public PasswordAuth(String password) {
        this.password = Objects.requireNonNull(password, "password");
    }

    @Override public String methodName() { return "password"; }
    @Override public boolean isInteractive() { return false; }

    @Override
    public byte[] encodeRequest(String username, String serviceName) {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        buf.put((byte) 50); // SSH_MSG_USERAUTH_REQUEST
        SshTransportCodec.writeString(buf, username);
        SshTransportCodec.writeString(buf, serviceName);
        SshTransportCodec.writeString(buf, "password");
        SshTransportCodec.writeBoolean(buf, false); // not a password change
        SshTransportCodec.writeString(buf, password);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
