package ssg.legoflow.messaging.amqp.sasl;

/**
 * SASL authentication mechanism for AMQP 1.0 connections.
 *
 * @since 0.1.0
 */
public interface SaslMechanism {

    /**
     * Returns the mechanism name (e.g. ANONYMOUS, PLAIN, EXTERNAL).
     *
     * @return the mechanism name
     */
    String name();

    /**
     * Returns the initial response bytes for sasl-init.
     *
     * @return the initial response, or empty byte array
     */
    byte[] initialResponse();

    /**
     * Processes a challenge from the server and returns the response.
     *
     * @param challenge the challenge bytes
     * @return the response bytes
     */
    byte[] respond(byte[] challenge);
}
