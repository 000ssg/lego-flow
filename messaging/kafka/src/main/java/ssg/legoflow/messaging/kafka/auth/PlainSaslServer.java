package ssg.legoflow.messaging.kafka.auth;

import java.nio.charset.StandardCharsets;

/**
 * SASL PLAIN mechanism server (RFC 4616).
 *
 * <p>The client sends a single message containing three NUL-separated fields:
 * {@code authzid \0 authcid \0 password}. The authzid (authorization identity)
 * may be empty. Authentication is single-step.
 *
 * @since 0.1.0
 */
public final class PlainSaslServer implements SaslMechanism {

    private final CredentialStore credentialStore;
    private boolean complete;
    private String authenticatedUser;

    /**
     * Creates a PLAIN SASL server.
     *
     * @param credentialStore the credential store for validation
     */
    public PlainSaslServer(CredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    @Override
    public String mechanismName() {
        return "PLAIN";
    }

    @Override
    public byte[] evaluateResponse(byte[] clientMessage) throws AuthenticationException {
        if (clientMessage == null || clientMessage.length == 0) {
            throw new AuthenticationException("Empty PLAIN authentication message");
        }

        // Parse: authzid \0 authcid \0 password
        String message = new String(clientMessage, StandardCharsets.UTF_8);

        // Find first NUL separator
        int firstNul = indexOf(clientMessage, (byte) 0, 0);
        if (firstNul < 0) {
            throw new AuthenticationException("Malformed PLAIN message: missing first NUL separator");
        }

        // Find second NUL separator
        int secondNul = indexOf(clientMessage, (byte) 0, firstNul + 1);
        if (secondNul < 0) {
            throw new AuthenticationException("Malformed PLAIN message: missing second NUL separator");
        }

        // authzid is bytes 0..firstNul-1 (may be empty)
        String authcid = new String(clientMessage, firstNul + 1, secondNul - firstNul - 1, StandardCharsets.UTF_8);
        String password = new String(clientMessage, secondNul + 1, clientMessage.length - secondNul - 1, StandardCharsets.UTF_8);

        if (authcid.isEmpty()) {
            throw new AuthenticationException("Empty username in PLAIN message");
        }

        if (!credentialStore.validatePlain(authcid, password)) {
            throw new AuthenticationException("Authentication failed for user: " + authcid);
        }

        this.authenticatedUser = authcid;
        this.complete = true;
        return new byte[0]; // No server response needed for PLAIN
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public String authenticatedUser() {
        return authenticatedUser;
    }

    private static int indexOf(byte[] data, byte target, int from) {
        for (int i = from; i < data.length; i++) {
            if (data[i] == target) return i;
        }
        return -1;
    }
}
