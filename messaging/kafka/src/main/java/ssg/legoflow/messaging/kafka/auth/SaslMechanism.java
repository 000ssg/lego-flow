package ssg.legoflow.messaging.kafka.auth;

/**
 * Interface for SASL authentication mechanisms.
 *
 * <p>Implementations handle one or more rounds of challenge-response exchange
 * between client and server during SASL authentication.
 *
 * @since 1.0.0
 */
public interface SaslMechanism {

    /**
     * Returns the IANA-registered mechanism name (e.g., "PLAIN", "SCRAM-SHA-256").
     *
     * @return the mechanism name
     */
    String mechanismName();

    /**
     * Evaluates a client message and returns a server response.
     *
     * @param clientMessage the bytes sent by the client
     * @return the response bytes to send back, or empty array if none needed
     * @throws AuthenticationException if authentication fails
     */
    byte[] evaluateResponse(byte[] clientMessage) throws AuthenticationException;

    /**
     * Returns whether the authentication exchange is complete.
     *
     * @return true if authentication is finished
     */
    boolean isComplete();

    /**
     * Returns the authenticated username after a successful exchange.
     *
     * @return the username, or null if not yet authenticated
     */
    String authenticatedUser();
}
