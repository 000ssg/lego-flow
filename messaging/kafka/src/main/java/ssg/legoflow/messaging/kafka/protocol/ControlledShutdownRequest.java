package ssg.legoflow.messaging.kafka.protocol;

/**
 * ControlledShutdown request (API key 7).
 *
 * <p>Sent by a broker to the controller to request a graceful shutdown.
 *
 * @param brokerId the broker ID requesting shutdown
 * @since 1.0.0
 */
public record ControlledShutdownRequest(int brokerId) {
}
