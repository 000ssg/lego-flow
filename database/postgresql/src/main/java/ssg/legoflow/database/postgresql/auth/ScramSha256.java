package ssg.legoflow.database.postgresql.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * SCRAM-SHA-256 authentication (RFC 5802) for PostgreSQL.
 *
 * <p>Implements both client-side and server-side of the SCRAM-SHA-256 handshake:
 * <ol>
 *   <li>Client sends client-first-message: {@code n,,n=user,r=client-nonce}</li>
 *   <li>Server responds with server-first-message: {@code r=combined-nonce,s=salt,i=iterations}</li>
 *   <li>Client sends client-final-message: {@code c=biws,r=combined-nonce,p=client-proof}</li>
 *   <li>Server responds with server-final-message: {@code v=server-signature}</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class ScramSha256 implements PgAuthenticator {

    /** The SASL mechanism name. */
    public static final String MECHANISM = "SCRAM-SHA-256";

    /** Default iteration count. */
    public static final int DEFAULT_ITERATIONS = 4096;

    private final Map<String, StoredCredentials> credentials = new ConcurrentHashMap<>();
    private int iterations = DEFAULT_ITERATIONS;

    /**
     * Stored SCRAM credentials for a user.
     *
     * @param salt       the salt bytes
     * @param storedKey  the StoredKey
     * @param serverKey  the ServerKey
     * @param iterations the iteration count
     */
    public record StoredCredentials(byte[] salt, byte[] storedKey, byte[] serverKey, int iterations) {}

    /**
     * Creates a new SCRAM-SHA-256 authenticator.
     */
    public ScramSha256() {}

    /**
     * Sets the iteration count for new users.
     *
     * @param iterations the iteration count
     * @return this for chaining
     */
    public ScramSha256 withIterations(int iterations) {
        this.iterations = iterations;
        return this;
    }

    /**
     * Registers a user with a password, computing SCRAM credentials.
     *
     * @param username the username
     * @param password the password
     * @return this for chaining
     */
    public ScramSha256 addUser(String username, String password) {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        addUser(username, password, salt, iterations);
        return this;
    }

    /**
     * Registers a user with a password and specific salt.
     *
     * @param username   the username
     * @param password   the password
     * @param salt       the salt bytes
     * @param iterations the iteration count
     * @return this for chaining
     */
    public ScramSha256 addUser(String username, String password, byte[] salt, int iterations) {
        byte[] saltedPassword = ScramUtils.hi(
                ScramUtils.toBytes(password), salt, iterations);
        byte[] clientKey = ScramUtils.hmac(saltedPassword, ScramUtils.toBytes("Client Key"));
        byte[] storedKey = ScramUtils.hash(clientKey);
        byte[] serverKey = ScramUtils.hmac(saltedPassword, ScramUtils.toBytes("Server Key"));
        credentials.put(username, new StoredCredentials(salt, storedKey, serverKey, iterations));
        return this;
    }

    /**
     * Returns the stored credentials for a user.
     *
     * @param username the username
     * @return the stored credentials, or null if user not found
     */
    public StoredCredentials getCredentials(String username) {
        return credentials.get(username);
    }

    @Override
    public String method() {
        return "scram-sha-256";
    }

    @Override
    public boolean authenticate(String username, String password) {
        StoredCredentials cred = credentials.get(username);
        if (cred == null) return false;

        byte[] saltedPassword = ScramUtils.hi(
                ScramUtils.toBytes(password), cred.salt(), cred.iterations());
        byte[] clientKey = ScramUtils.hmac(saltedPassword, ScramUtils.toBytes("Client Key"));
        byte[] storedKey = ScramUtils.hash(clientKey);
        return java.util.Arrays.equals(storedKey, cred.storedKey());
    }

    // ======== Server-side handshake ========

    /**
     * Server state for a SCRAM handshake in progress.
     */
    public static final class ServerSession {
        private final StoredCredentials cred;
        private final String serverNonce;
        private String clientFirstMessageBare;
        private String serverFirstMessage;
        private String username;

        /**
         * Creates a new server session.
         *
         * @param cred the stored credentials for the user
         */
        public ServerSession(StoredCredentials cred) {
            this.cred = cred;
            this.serverNonce = ScramUtils.generateNonce();
        }

        /**
         * Processes the client-first-message and returns the server-first-message.
         *
         * @param clientFirstMessage the full client-first-message (e.g., "n,,n=user,r=nonce")
         * @return the server-first-message
         */
        public String processClientFirst(String clientFirstMessage) {
            // Strip "n,," GS2 header
            int headerEnd = clientFirstMessage.indexOf(',', clientFirstMessage.indexOf(',') + 1);
            clientFirstMessageBare = clientFirstMessage.substring(headerEnd + 1);

            // Parse username and nonce
            Map<String, String> attrs = parseAttributes(clientFirstMessageBare);
            username = attrs.get("n");
            String clientNonce = attrs.get("r");

            String combinedNonce = clientNonce + serverNonce;
            serverFirstMessage = "r=" + combinedNonce
                    + ",s=" + ScramUtils.base64Encode(cred.salt())
                    + ",i=" + cred.iterations();
            return serverFirstMessage;
        }

        /**
         * Processes the client-final-message and returns the server-final-message.
         *
         * @param clientFinalMessage the client-final-message
         * @return the server-final-message, or null if authentication fails
         */
        public String processClientFinal(String clientFinalMessage) {
            Map<String, String> attrs = parseAttributes(clientFinalMessage);
            String clientProofB64 = attrs.get("p");

            // Reconstruct client-final-message-without-proof
            int proofIdx = clientFinalMessage.lastIndexOf(",p=");
            String clientFinalWithoutProof = clientFinalMessage.substring(0, proofIdx);

            String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalWithoutProof;

            byte[] clientProof = ScramUtils.base64Decode(clientProofB64);
            byte[] clientSignature = ScramUtils.hmac(cred.storedKey(), ScramUtils.toBytes(authMessage));
            byte[] clientKey = ScramUtils.xor(clientProof, clientSignature);
            byte[] storedKey = ScramUtils.hash(clientKey);

            if (!java.util.Arrays.equals(storedKey, cred.storedKey())) {
                return null; // Authentication failed
            }

            byte[] serverSignature = ScramUtils.hmac(cred.serverKey(), ScramUtils.toBytes(authMessage));
            return "v=" + ScramUtils.base64Encode(serverSignature);
        }
    }

    // ======== Client-side handshake ========

    /**
     * Client state for a SCRAM handshake in progress.
     */
    public static final class ClientSession {
        private final String username;
        private final String password;
        private final String clientNonce;
        private String clientFirstMessageBare;
        private String serverFirstMessage;

        /**
         * Creates a new client session.
         *
         * @param username the username
         * @param password the password
         */
        public ClientSession(String username, String password) {
            this.username = username;
            this.password = password;
            this.clientNonce = ScramUtils.generateNonce();
        }

        /**
         * Creates a client session with a specific nonce (for testing).
         *
         * @param username    the username
         * @param password    the password
         * @param clientNonce the client nonce
         */
        public ClientSession(String username, String password, String clientNonce) {
            this.username = username;
            this.password = password;
            this.clientNonce = clientNonce;
        }

        /**
         * Generates the client-first-message.
         *
         * @return the client-first-message bytes (e.g., "n,,n=user,r=nonce")
         */
        public String createClientFirstMessage() {
            clientFirstMessageBare = "n=" + username + ",r=" + clientNonce;
            return "n,," + clientFirstMessageBare;
        }

        /**
         * Processes the server-first-message and returns the client-final-message.
         *
         * @param serverFirst the server-first-message
         * @return the client-final-message
         */
        public String processServerFirst(String serverFirst) {
            this.serverFirstMessage = serverFirst;
            Map<String, String> attrs = parseAttributes(serverFirst);
            String combinedNonce = attrs.get("r");
            byte[] salt = ScramUtils.base64Decode(attrs.get("s"));
            int iterations = Integer.parseInt(attrs.get("i"));

            // Verify server nonce starts with client nonce
            if (!combinedNonce.startsWith(clientNonce)) {
                throw new IllegalStateException("Server nonce does not start with client nonce");
            }

            String channelBinding = ScramUtils.base64Encode(ScramUtils.toBytes("n,,"));
            String clientFinalWithoutProof = "c=" + channelBinding + ",r=" + combinedNonce;
            String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalWithoutProof;

            byte[] saltedPassword = ScramUtils.hi(ScramUtils.toBytes(password), salt, iterations);
            byte[] clientKey = ScramUtils.hmac(saltedPassword, ScramUtils.toBytes("Client Key"));
            byte[] storedKey = ScramUtils.hash(clientKey);
            byte[] clientSignature = ScramUtils.hmac(storedKey, ScramUtils.toBytes(authMessage));
            byte[] clientProof = ScramUtils.xor(clientKey, clientSignature);

            return clientFinalWithoutProof + ",p=" + ScramUtils.base64Encode(clientProof);
        }

        /**
         * Verifies the server-final-message.
         *
         * @param serverFinal the server-final-message
         * @return true if the server signature is valid
         */
        public boolean verifyServerFinal(String serverFinal) {
            Map<String, String> attrs = parseAttributes(serverFinal);
            String receivedSignature = attrs.get("v");
            if (receivedSignature == null) return false;

            Map<String, String> sfAttrs = parseAttributes(serverFirstMessage);
            String combinedNonce = sfAttrs.get("r");
            byte[] salt = ScramUtils.base64Decode(sfAttrs.get("s"));
            int iterations = Integer.parseInt(sfAttrs.get("i"));

            String channelBinding = ScramUtils.base64Encode(ScramUtils.toBytes("n,,"));
            String clientFinalWithoutProof = "c=" + channelBinding + ",r=" + combinedNonce;
            String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalWithoutProof;

            byte[] saltedPassword = ScramUtils.hi(ScramUtils.toBytes(password), salt, iterations);
            byte[] serverKey = ScramUtils.hmac(saltedPassword, ScramUtils.toBytes("Server Key"));
            byte[] expectedSignature = ScramUtils.hmac(serverKey, ScramUtils.toBytes(authMessage));

            return receivedSignature.equals(ScramUtils.base64Encode(expectedSignature));
        }
    }

    // ======== Utilities ========

    /**
     * Parses comma-separated key=value attributes.
     *
     * @param message the message string
     * @return the parsed attributes
     */
    static Map<String, String> parseAttributes(String message) {
        Map<String, String> attrs = new java.util.LinkedHashMap<>();
        for (String part : message.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                attrs.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        return attrs;
    }
}
