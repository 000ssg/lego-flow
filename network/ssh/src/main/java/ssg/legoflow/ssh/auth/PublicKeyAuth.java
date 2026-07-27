package ssg.legoflow.ssh.auth;

import ssg.legoflow.ssh.hostkey.SshKeyPair;
import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Public key authentication method per RFC 4252 section 7.
 *
 * @since 1.0.0
 */
public final class PublicKeyAuth implements AuthMethod {

    private final SshKeyPair keyPair;
    private byte[] sessionId;

    /**
     * Creates a public key authentication method.
     *
     * @param keyPair   the key pair to authenticate with
     * @param sessionId the session ID for signing
     */
    public PublicKeyAuth(SshKeyPair keyPair, byte[] sessionId) {
        this.keyPair = Objects.requireNonNull(keyPair, "keyPair");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    @Override public String methodName() { return "publickey"; }
    @Override public boolean isInteractive() { return false; }

    @Override
    public byte[] encodeRequest(String username, String serviceName) {
        byte[] publicKeyBlob = keyPair.publicKeyBlob();

        // Build the data to sign per RFC 4252 §7
        ByteBuffer signData = ByteBuffer.allocate(4096);
        SshTransportCodec.writeBinary(signData, sessionId);
        signData.put((byte) 50); // SSH_MSG_USERAUTH_REQUEST
        SshTransportCodec.writeString(signData, username);
        SshTransportCodec.writeString(signData, serviceName);
        SshTransportCodec.writeString(signData, "publickey");
        SshTransportCodec.writeBoolean(signData, true);
        SshTransportCodec.writeString(signData, keyPair.algorithm());
        SshTransportCodec.writeBinary(signData, publicKeyBlob);
        signData.flip();
        byte[] toSign = new byte[signData.remaining()];
        signData.get(toSign);

        byte[] signature = keyPair.sign(toSign);

        // Build the request
        ByteBuffer buf = ByteBuffer.allocate(4096);
        buf.put((byte) 50);
        SshTransportCodec.writeString(buf, username);
        SshTransportCodec.writeString(buf, serviceName);
        SshTransportCodec.writeString(buf, "publickey");
        SshTransportCodec.writeBoolean(buf, true);
        SshTransportCodec.writeString(buf, keyPair.algorithm());
        SshTransportCodec.writeBinary(buf, publicKeyBlob);
        SshTransportCodec.writeBinary(buf, signature);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
