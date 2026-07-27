package ssg.legoflow.messaging.kafka.protocol;

/**
 * Kafka request header sent by clients.
 *
 * @param apiKey        the API key
 * @param apiVersion    the API version
 * @param correlationId the correlation ID for matching request/response
 * @param clientId      the client identifier (nullable)
 * @since 1.0.0
 */
public record RequestHeader(short apiKey, short apiVersion, int correlationId, String clientId) {
}
