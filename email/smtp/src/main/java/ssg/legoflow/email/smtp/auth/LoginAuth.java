package ssg.legoflow.email.smtp.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * LOGIN SASL authentication mechanism (non-standard, widely used).
 *
 * <p>The LOGIN mechanism uses a two-step challenge-response exchange:
 * <ol>
 *   <li>Server sends 334 with Base64("Username:")</li>
 *   <li>Client sends Base64(username)</li>
 *   <li>Server sends 334 with Base64("Password:")</li>
 *   <li>Client sends Base64(password)</li>
 * </ol>
 *
 * <p>This mechanism should only be used over TLS connections, as credentials
 * are sent in cleartext.
 *
 * @since 0.1.0
 */
public final class LoginAuth implements SmtpAuthenticator {

    private static final String USERNAME_CHALLENGE = "Username:";
    private static final String PASSWORD_CHALLENGE = "Password:";

    private final String username;
    private final String password;
    private int step = 0;

    /**
     * Creates a LOGIN authenticator.
     *
     * @param username the username
     * @param password the password
     */
    public LoginAuth(String username, String password) {
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
    }

    @Override
    public String mechanism() {
        return "LOGIN";
    }

    @Override
    public String initialResponse() {
        // LOGIN does not send an initial response
        return null;
    }

    @Override
    public String respond(String challenge) throws SmtpAuthException {
        return switch (step++) {
            case 0 -> Base64.getEncoder().encodeToString(
                    username.getBytes(StandardCharsets.UTF_8));
            case 1 -> Base64.getEncoder().encodeToString(
                    password.getBytes(StandardCharsets.UTF_8));
            default -> throw new SmtpAuthException("LOGIN mechanism: unexpected challenge at step " + step);
        };
    }

    @Override
    public boolean isComplete() {
        return step >= 2;
    }

    /**
     * Returns the standard username challenge string.
     *
     * @return "Username:" Base64-encoded
     */
    public static String usernameChallenge() {
        return Base64.getEncoder().encodeToString(
                USERNAME_CHALLENGE.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the standard password challenge string.
     *
     * @return "Password:" Base64-encoded
     */
    public static String passwordChallenge() {
        return Base64.getEncoder().encodeToString(
                PASSWORD_CHALLENGE.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a Base64-encoded LOGIN response (username or password).
     *
     * @param base64Response the Base64-encoded response
     * @return the decoded string
     */
    public static String decodeResponse(String base64Response) {
        byte[] decoded = Base64.getDecoder().decode(base64Response.trim());
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
