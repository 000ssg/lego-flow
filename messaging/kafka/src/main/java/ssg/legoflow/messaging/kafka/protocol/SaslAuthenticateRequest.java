package ssg.legoflow.messaging.kafka.protocol;

/**
 * SaslAuthenticate request (API key 36) for performing SASL authentication exchange.
 *
 * @param authBytes the authentication bytes from the client
 * @since 1.0.0
 */
public record SaslAuthenticateRequest(byte[] authBytes) {
}
