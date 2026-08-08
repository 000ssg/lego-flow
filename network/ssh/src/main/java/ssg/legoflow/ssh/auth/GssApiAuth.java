package ssg.legoflow.ssh.auth;

import ssg.legoflow.auth.gssapi.GssContextWrapper;
import ssg.legoflow.auth.gssapi.GssException;
import ssg.legoflow.auth.gssapi.GssOids;
import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * GSSAPI authentication method per RFC 4462 (SSH GSSAPI Authentication).
 *
 * <p>Implements the "gssapi-with-mic" authentication method, which uses the
 * GSS-API framework (typically with Kerberos V5) to authenticate SSH users.
 * This is a multi-round-trip method requiring token exchange between client
 * and server.</p>
 *
 * <p>The authentication flow is:
 * <ol>
 *   <li>Client sends SSH_MSG_USERAUTH_REQUEST with method "gssapi-with-mic" and supported OIDs</li>
 *   <li>Server responds with SSH_MSG_USERAUTH_GSSAPI_RESPONSE (selected OID)</li>
 *   <li>Client/Server exchange SSH_MSG_USERAUTH_GSSAPI_TOKEN messages until context is established</li>
 *   <li>Client sends SSH_MSG_USERAUTH_GSSAPI_MIC to prove possession of session key</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class GssApiAuth implements AuthMethod {

    /** SSH message type for GSSAPI token exchange (RFC 4462). */
    private static final byte SSH_MSG_USERAUTH_GSSAPI_TOKEN = 61;

    /** SSH message type for GSSAPI MIC (RFC 4462). */
    private static final byte SSH_MSG_USERAUTH_GSSAPI_MIC = 66;

    private final GssContextWrapper context;
    private boolean complete;

    /**
     * Creates a GSSAPI authentication method using the provided GSS context.
     *
     * @param context the GSS context wrapper for token exchange
     * @throws NullPointerException if context is null
     * @since 0.1.0
     */
    public GssApiAuth(GssContextWrapper context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.complete = false;
    }

    @Override
    public String methodName() {
        return "gssapi-with-mic";
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    /**
     * Encodes the initial SSH_MSG_USERAUTH_REQUEST for GSSAPI authentication.
     *
     * <p>The request contains:
     * <ul>
     *   <li>byte: SSH_MSG_USERAUTH_REQUEST (50)</li>
     *   <li>string: username</li>
     *   <li>string: service name (e.g., "ssh-connection")</li>
     *   <li>string: "gssapi-with-mic"</li>
     *   <li>uint32: number of OIDs supported (1)</li>
     *   <li>string: OID bytes for Kerberos V5</li>
     * </ul>
     *
     * @param username    the user name
     * @param serviceName the service name
     * @return the encoded authentication request payload
     * @since 0.1.0
     */
    @Override
    public byte[] encodeRequest(String username, String serviceName) {
        byte[] oidDer;
        try {
            oidDer = GssOids.KERBEROS_V5.getDER();
        } catch (org.ietf.jgss.GSSException e) {
            throw new IllegalStateException("Failed to encode Kerberos OID", e);
        }

        ByteBuffer buf = ByteBuffer.allocate(1024);
        buf.put((byte) 50); // SSH_MSG_USERAUTH_REQUEST
        SshTransportCodec.writeString(buf, username);
        SshTransportCodec.writeString(buf, serviceName);
        SshTransportCodec.writeString(buf, "gssapi-with-mic");
        SshTransportCodec.writeUint32(buf, 1); // number of OIDs
        SshTransportCodec.writeBinary(buf, oidDer); // Kerberos V5 OID
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Handles a GSSAPI token response from the server and produces the next
     * token to send (SSH_MSG_USERAUTH_GSSAPI_TOKEN).
     *
     * @param responseData the token data received from the server
     * @return the next token message to send, or empty array if context is established
     * @throws GssException if token processing fails
     * @since 0.1.0
     */
    public byte[] handleResponse(byte[] responseData) throws GssException {
        Objects.requireNonNull(responseData, "responseData must not be null");

        byte[] outputToken = context.initSecContext(responseData);

        if (context.isEstablished()) {
            complete = true;
        }

        if (outputToken.length == 0) {
            return outputToken;
        }

        // Wrap in SSH_MSG_USERAUTH_GSSAPI_TOKEN
        ByteBuffer buf = ByteBuffer.allocate(outputToken.length + 64);
        buf.put(SSH_MSG_USERAUTH_GSSAPI_TOKEN);
        SshTransportCodec.writeBinary(buf, outputToken);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Creates the SSH_MSG_USERAUTH_GSSAPI_MIC message to complete authentication.
     *
     * <p>The MIC is computed over the session identifier, username, service name,
     * and method name, proving that the client holds the GSS session key.</p>
     *
     * @param sessionId   the SSH session identifier
     * @param username    the username being authenticated
     * @param serviceName the service name (e.g., "ssh-connection")
     * @return the encoded MIC message
     * @throws GssException if MIC computation fails
     * @since 0.1.0
     */
    public byte[] createMIC(byte[] sessionId, String username, String serviceName) throws GssException {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(serviceName, "serviceName must not be null");

        // Build the MIC data: session_id + SSH_MSG_USERAUTH_REQUEST fields
        ByteBuffer micData = ByteBuffer.allocate(1024);
        SshTransportCodec.writeBinary(micData, sessionId);
        micData.put((byte) 50); // SSH_MSG_USERAUTH_REQUEST
        SshTransportCodec.writeString(micData, username);
        SshTransportCodec.writeString(micData, serviceName);
        SshTransportCodec.writeString(micData, "gssapi-with-mic");
        micData.flip();
        byte[] micInput = new byte[micData.remaining()];
        micData.get(micInput);

        byte[] mic = context.getMIC(micInput);

        // Wrap in SSH_MSG_USERAUTH_GSSAPI_MIC
        ByteBuffer buf = ByteBuffer.allocate(mic.length + 64);
        buf.put(SSH_MSG_USERAUTH_GSSAPI_MIC);
        SshTransportCodec.writeBinary(buf, mic);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Returns whether the GSSAPI context establishment is complete.
     *
     * @return true if the GSS context is fully established
     * @since 0.1.0
     */
    public boolean isComplete() {
        return complete;
    }
}
