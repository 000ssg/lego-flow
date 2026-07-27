package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * SaslHandshake response (API key 17).
 *
 * @param errorCode  the error code (0 for success, 33 for unsupported mechanism)
 * @param mechanisms the list of server-supported SASL mechanisms
 * @since 1.0.0
 */
public record SaslHandshakeResponse(short errorCode, List<String> mechanisms) {
}
