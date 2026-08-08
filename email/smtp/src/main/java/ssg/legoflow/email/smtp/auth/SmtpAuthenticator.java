package ssg.legoflow.email.smtp.auth;

/**
 * Interface for SMTP SASL authentication mechanisms.
 *
 * <p>Implementations handle the client-side or server-side of an SMTP AUTH
 * exchange. The authentication follows a challenge-response pattern:
 * <ol>
 *   <li>Client sends {@code AUTH mechanism [initial-response]}</li>
 *   <li>Server sends {@code 334 challenge} (Base64-encoded)</li>
 *   <li>Client sends response (Base64-encoded)</li>
 *   <li>Steps 2-3 repeat as needed</li>
 *   <li>Server sends {@code 235 Authentication successful} or {@code 535 Authentication failed}</li>
 * </ol>
 *
 * @since 0.1.0
 */
public interface SmtpAuthenticator {

    /**
     * Returns the SASL mechanism name (e.g., "PLAIN", "LOGIN", "CRAM-MD5").
     *
     * @return the mechanism name (uppercase)
     */
    String mechanism();

    /**
     * Returns the initial response to send with the AUTH command, or {@code null}
     * if no initial response is supported.
     *
     * <p>For PLAIN, this is the Base64-encoded credentials.
     * For LOGIN and CRAM-MD5, this returns {@code null}.
     *
     * @return the initial response (Base64-encoded), or {@code null}
     */
    String initialResponse();

    /**
     * Processes a server challenge and returns the response.
     *
     * @param challenge the Base64-decoded server challenge
     * @return the Base64-encoded response
     * @throws SmtpAuthException if the challenge is invalid
     */
    String respond(String challenge) throws SmtpAuthException;

    /**
     * Returns {@code true} if this authenticator has completed its exchange.
     *
     * @return true if authentication exchange is complete
     */
    boolean isComplete();
}
