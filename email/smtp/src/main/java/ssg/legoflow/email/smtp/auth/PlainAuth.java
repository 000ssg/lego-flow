package ssg.legoflow.email.smtp.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * PLAIN SASL authentication mechanism per RFC 4616.
 *
 * <p>The PLAIN mechanism sends credentials as a single Base64-encoded string
 * containing: {@code \0authzid\0authcid\0password} (with NUL separators).
 * For SMTP, authzid is typically empty (same as authcid).
 *
 * <p>This mechanism should only be used over TLS connections, as credentials
 * are sent in cleartext (Base64 is encoding, not encryption).
 *
 * @since 0.1.0
 */
public final class PlainAuth implements SmtpAuthenticator {

    private final String username;
    private final String password;
    private final String authzId;
    private boolean complete = false;

    /**
     * Creates a PLAIN authenticator.
     *
     * @param username the username (authcid)
     * @param password the password
     */
    public PlainAuth(String username, String password) {
        this(username, password, "");
    }

    /**
     * Creates a PLAIN authenticator with an authorization identity.
     *
     * @param username the username (authcid)
     * @param password the password
     * @param authzId  the authorization identity (may be empty)
     */
    public PlainAuth(String username, String password, String authzId) {
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.authzId = Objects.requireNonNull(authzId, "authzId");
    }

    @Override
    public String mechanism() {
        return "PLAIN";
    }

    @Override
    public String initialResponse() {
        byte[] credentials = (authzId + "\0" + username + "\0" + password)
                .getBytes(StandardCharsets.UTF_8);
        complete = true;
        return Base64.getEncoder().encodeToString(credentials);
    }

    @Override
    public String respond(String challenge) throws SmtpAuthException {
        // PLAIN sends everything in the initial response; if the server
        // sends a challenge (empty 334), respond with credentials
        if (!complete) {
            return initialResponse();
        }
        throw new SmtpAuthException("PLAIN mechanism does not expect additional challenges");
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    /**
     * Decodes PLAIN credentials from a Base64-encoded string.
     *
     * @param base64Credentials the Base64-encoded credentials
     * @return three-element array: [authzid, authcid, password]
     * @throws SmtpAuthException if the format is invalid
     */
    public static String[] decodeCredentials(String base64Credentials) throws SmtpAuthException {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Credentials);
        } catch (IllegalArgumentException e) {
            throw new SmtpAuthException("Invalid Base64 in PLAIN credentials", e);
        }
        String creds = new String(decoded, StandardCharsets.UTF_8);
        // Format: authzid\0authcid\0password
        int first = creds.indexOf('\0');
        if (first < 0) {
            throw new SmtpAuthException("Invalid PLAIN credentials format: missing NUL separator");
        }
        int second = creds.indexOf('\0', first + 1);
        if (second < 0) {
            throw new SmtpAuthException("Invalid PLAIN credentials format: missing second NUL separator");
        }
        return new String[]{
                creds.substring(0, first),
                creds.substring(first + 1, second),
                creds.substring(second + 1)
        };
    }
}
