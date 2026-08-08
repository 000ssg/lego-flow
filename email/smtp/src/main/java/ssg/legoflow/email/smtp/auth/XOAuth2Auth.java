package ssg.legoflow.email.smtp.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * XOAUTH2 SASL authentication mechanism (Google extension).
 *
 * <p>Used with Gmail and Google Workspace for OAuth 2.0 bearer token authentication.
 * The mechanism sends credentials as a single Base64-encoded string containing:
 * {@code user=email\001auth=Bearer token\001\001}
 *
 * <p>Format details:
 * <ul>
 *   <li>{@code \001} is the SOH (Start of Heading) character, ASCII 0x01</li>
 *   <li>The string ends with two SOH characters</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class XOAuth2Auth implements SmtpAuthenticator {

    private static final char SOH = '\001';

    private final String email;
    private final String accessToken;
    private boolean complete = false;

    /**
     * Creates an XOAUTH2 authenticator.
     *
     * @param email       the user's email address
     * @param accessToken the OAuth 2.0 access token
     */
    public XOAuth2Auth(String email, String accessToken) {
        this.email = Objects.requireNonNull(email, "email");
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken");
    }

    @Override
    public String mechanism() {
        return "XOAUTH2";
    }

    @Override
    public String initialResponse() {
        String payload = "user=" + email + SOH + "auth=Bearer " + accessToken + SOH + SOH;
        complete = true;
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String respond(String challenge) throws SmtpAuthException {
        if (complete) {
            // XOAUTH2 expects empty response to error challenge
            return "";
        }
        return initialResponse();
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    /**
     * Decodes XOAUTH2 credentials from a Base64-encoded string.
     *
     * @param base64Credentials the Base64-encoded credentials
     * @return two-element array: [email, accessToken]
     * @throws SmtpAuthException if the format is invalid
     */
    public static String[] decodeCredentials(String base64Credentials) throws SmtpAuthException {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Credentials);
        } catch (IllegalArgumentException e) {
            throw new SmtpAuthException("Invalid Base64 in XOAUTH2 credentials", e);
        }
        String creds = new String(decoded, StandardCharsets.UTF_8);

        // Parse user=email\001auth=Bearer token\001\001
        if (!creds.startsWith("user=")) {
            throw new SmtpAuthException("XOAUTH2 credentials must start with 'user='");
        }
        int sohIdx = creds.indexOf(SOH);
        if (sohIdx < 0) {
            throw new SmtpAuthException("Invalid XOAUTH2 credentials format");
        }
        String email = creds.substring(5, sohIdx);

        String rest = creds.substring(sohIdx + 1);
        if (!rest.startsWith("auth=Bearer ")) {
            throw new SmtpAuthException("XOAUTH2 credentials must contain 'auth=Bearer'");
        }
        int tokenEnd = rest.indexOf(SOH);
        if (tokenEnd < 0) {
            throw new SmtpAuthException("Invalid XOAUTH2 credentials format");
        }
        String token = rest.substring("auth=Bearer ".length(), tokenEnd);

        return new String[]{email, token};
    }
}
