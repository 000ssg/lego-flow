package ssg.legoflow.messaging.kafka.protocol;

/**
 * FindCoordinator response (API key 10).
 *
 * @param errorCode the error code
 * @param nodeId    the coordinator broker ID
 * @param host      the coordinator hostname
 * @param port      the coordinator port
 * @since 0.1.0
 */
public record FindCoordinatorResponse(short errorCode, int nodeId, String host, int port) {
}
