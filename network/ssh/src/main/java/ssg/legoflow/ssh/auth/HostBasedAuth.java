package ssg.legoflow.ssh.auth;

import ssg.legoflow.ssh.hostkey.SshKeyPair;
import ssg.legoflow.ssh.transport.SshTransportCodec;
import java.nio.ByteBuffer;
import java.util.Objects;
/**
 * Host-based authentication method per RFC 4252 section 9.
 *
 * @since 0.1.0
 */
public final class HostBasedAuth implements AuthMethod {

    private final SshKeyPair hostKey;
    private final String clientHostname;
    private final String clientUsername;
    private final byte[] sessionId;

    /**
     * Creates a host-based authentication method.
     *
     * @param hostKey        the host key to authenticate with
     * @param clientHostname the client's hostname (FQDN)
     * @param clientUsername the client-side username
     * @param sessionId      the session ID
     */
    public HostBasedAuth(SshKeyPair hostKey, String clientHostname,
                         String clientUsername, byte[] sessionId) {
        this.hostKey = Objects.requireNonNull(hostKey, "hostKey");
        this.clientHostname = Objects.requireNonNull(clientHostname, "clientHostname");
        this.clientUsername = Objects.requireNonNull(clientUsername, "clientUsername");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    @Override public String methodName() { return "hostbased"; }
    @Override public boolean isInteractive() { return false; }

    @Override
    public byte[] encodeRequest(String username, String serviceName) {
        byte[] publicKeyBlob = hostKey.publicKeyBlob();

        // Build data to sign
        ByteBuffer signData = ByteBuffer.allocate(4096);
        SshTransportCodec.writeBinary(signData, sessionId);
        signData.put((byte) 50);
        SshTransportCodec.writeString(signData, username);
        SshTransportCodec.writeString(signData, serviceName);
        SshTransportCodec.writeString(signData, "hostbased");
        SshTransportCodec.writeString(signData, hostKey.algorithm());
        SshTransportCodec.writeBinary(signData, publicKeyBlob);
        SshTransportCodec.writeString(signData, clientHostname);
        SshTransportCodec.writeString(signData, clientUsername);
        signData.flip();
        byte[] toSign = new byte[signData.remaining()];
        signData.get(toSign);

        byte[] signature = hostKey.sign(toSign);

        // Build request
        ByteBuffer buf = ByteBuffer.allocate(4096);
        buf.put((byte) 50);
        SshTransportCodec.writeString(buf, username);
        SshTransportCodec.writeString(buf, serviceName);
        SshTransportCodec.writeString(buf, "hostbased");
        SshTransportCodec.writeString(buf, hostKey.algorithm());
        SshTransportCodec.writeBinary(buf, publicKeyBlob);
        SshTransportCodec.writeString(buf, clientHostname);
        SshTransportCodec.writeString(buf, clientUsername);
        SshTransportCodec.writeBinary(buf, signature);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
