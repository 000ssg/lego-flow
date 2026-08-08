package ssg.legoflow.messaging.kafka.protocol;

/**
 * SaslHandshake request (API key 17) for negotiating SASL mechanism.
 *
 * @param mechanism the SASL mechanism name (e.g., "PLAIN", "SCRAM-SHA-256")
 * @since 0.1.0
 */
public record SaslHandshakeRequest(String mechanism) {
}
