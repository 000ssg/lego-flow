package ssg.legoflow.messaging.kafka.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SASL SCRAM-SHA-256 mechanism server (RFC 7677 / RFC 5802).
 *
 * <p>Implements the server side of the three-step SCRAM exchange:
 * <ol>
 *   <li>Client sends client-first-message: {@code n,,n=user,r=clientNonce}</li>
 *   <li>Server responds with server-first-message: {@code r=combinedNonce,s=salt,i=iterations}</li>
 *   <li>Client sends client-final-message: {@code c=biws,r=combinedNonce,p=clientProof}</li>
 *   <li>Server responds with server-final-message: {@code v=serverSignature} (or error)</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class ScramSha256Server implements SaslMechanism {

    private enum State { INITIAL, SERVER_FIRST_SENT, COMPLETE, FAILED }

    private final CredentialStore credentialStore;
    private final SecureRandom secureRandom = new SecureRandom();

    private State state = State.INITIAL;
    private String authenticatedUser;

    // Saved between steps
    private String username;
    private String clientNonce;
    private String serverNonce;
    private String combinedNonce;
    private String clientFirstMessageBare;
    private String serverFirstMessage;
    private CredentialStore.ScramCredential credential;

    /**
     * Creates a SCRAM-SHA-256 SASL server.
     *
     * @param credentialStore the credential store for validation
     */
    public ScramSha256Server(CredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    @Override
    public String mechanismName() {
        return "SCRAM-SHA-256";
    }

    @Override
    public byte[] evaluateResponse(byte[] clientMessage) throws AuthenticationException {
        try {
            return switch (state) {
                case INITIAL -> handleClientFirst(clientMessage);
                case SERVER_FIRST_SENT -> handleClientFinal(clientMessage);
                default -> throw new AuthenticationException("Unexpected SCRAM state: " + state);
            };
        } catch (AuthenticationException e) {
            state = State.FAILED;
            throw e;
        } catch (Exception e) {
            state = State.FAILED;
            throw new AuthenticationException("SCRAM authentication error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isComplete() {
        return state == State.COMPLETE;
    }

    @Override
    public String authenticatedUser() {
        return authenticatedUser;
    }

    /**
     * Step 1: Handle client-first-message.
     * Format: {@code n,,n=username,r=clientNonce}
     */
    private byte[] handleClientFirst(byte[] clientMessage) throws Exception {
        String msg = new String(clientMessage, StandardCharsets.UTF_8);

        // Parse GS2 header: must start with "n,," (no channel binding)
        if (!msg.startsWith("n,,")) {
            throw new AuthenticationException("Invalid SCRAM client-first-message: missing GS2 header");
        }

        // client-first-message-bare is everything after "n,,"
        clientFirstMessageBare = msg.substring(3);

        // Parse attributes
        String[] parts = clientFirstMessageBare.split(",");
        if (parts.length < 2) {
            throw new AuthenticationException("Invalid SCRAM client-first-message: too few fields");
        }

        // n=username
        if (!parts[0].startsWith("n=")) {
            throw new AuthenticationException("Invalid SCRAM client-first-message: missing n= attribute");
        }
        username = parts[0].substring(2);

        // r=clientNonce
        if (!parts[1].startsWith("r=")) {
            throw new AuthenticationException("Invalid SCRAM client-first-message: missing r= attribute");
        }
        clientNonce = parts[1].substring(2);

        // Look up credential
        credential = credentialStore.getScramCredential(username);
        if (credential == null) {
            throw new AuthenticationException("Unknown user: " + username);
        }

        // Generate server nonce
        byte[] nonceBytes = new byte[18];
        secureRandom.nextBytes(nonceBytes);
        serverNonce = Base64.getEncoder().encodeToString(nonceBytes);
        combinedNonce = clientNonce + serverNonce;

        // Build server-first-message
        String saltBase64 = Base64.getEncoder().encodeToString(credential.salt());
        serverFirstMessage = "r=" + combinedNonce + ",s=" + saltBase64 + ",i=" + credential.iterations();

        state = State.SERVER_FIRST_SENT;
        return serverFirstMessage.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Step 2: Handle client-final-message.
     * Format: {@code c=biws,r=combinedNonce,p=clientProof}
     */
    private byte[] handleClientFinal(byte[] clientMessage) throws Exception {
        String msg = new String(clientMessage, StandardCharsets.UTF_8);

        // Parse attributes
        String[] parts = msg.split(",");
        if (parts.length < 3) {
            throw new AuthenticationException("Invalid SCRAM client-final-message: too few fields");
        }

        // c=biws (base64 of "n,,")
        if (!parts[0].equals("c=biws")) {
            throw new AuthenticationException("Invalid channel binding in client-final-message");
        }

        // r=combinedNonce
        if (!parts[1].startsWith("r=")) {
            throw new AuthenticationException("Invalid SCRAM client-final-message: missing r= attribute");
        }
        String receivedNonce = parts[1].substring(2);
        if (!receivedNonce.equals(combinedNonce)) {
            throw new AuthenticationException("Nonce mismatch in SCRAM exchange");
        }

        // p=clientProof
        if (!parts[2].startsWith("p=")) {
            throw new AuthenticationException("Invalid SCRAM client-final-message: missing p= attribute");
        }
        byte[] clientProof = Base64.getDecoder().decode(parts[2].substring(2));

        // Verify client proof
        // AuthMessage = client-first-message-bare + "," + server-first-message + "," + client-final-message-without-proof
        String clientFinalWithoutProof = "c=biws,r=" + combinedNonce;
        String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalWithoutProof;

        // ClientSignature = HMAC(StoredKey, AuthMessage)
        byte[] clientSignature = CredentialStore.hmacSha256(
                credential.storedKey(), authMessage.getBytes(StandardCharsets.UTF_8));

        // ClientKey = ClientProof XOR ClientSignature
        byte[] clientKey = xor(clientProof, clientSignature);

        // StoredKey = H(ClientKey) — verify it matches
        byte[] computedStoredKey = CredentialStore.sha256(clientKey);
        if (!java.util.Arrays.equals(computedStoredKey, credential.storedKey())) {
            throw new AuthenticationException("Authentication failed for user: " + username);
        }

        // Compute server signature for verification response
        byte[] serverSignature = CredentialStore.hmacSha256(
                credential.serverKey(), authMessage.getBytes(StandardCharsets.UTF_8));

        authenticatedUser = username;
        state = State.COMPLETE;

        // server-final-message
        String serverFinal = "v=" + Base64.getEncoder().encodeToString(serverSignature);
        return serverFinal.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] xor(byte[] a, byte[] b) throws AuthenticationException {
        if (a.length != b.length) {
            throw new AuthenticationException("XOR operands have different lengths");
        }
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }
}
