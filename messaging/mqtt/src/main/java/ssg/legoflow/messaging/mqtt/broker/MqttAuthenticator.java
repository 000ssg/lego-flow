package ssg.legoflow.messaging.mqtt.broker;

/**
 * Pluggable authenticator for MQTT broker client connections.
 *
 * <p>Implementations validate username/password credentials provided
 * in the CONNECT packet. The broker calls {@link #authenticate} during
 * the connection handshake and rejects clients that fail authentication
 * with an appropriate CONNACK return code.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface MqttAuthenticator {

    /**
     * Authenticates a client connection attempt.
     *
     * @param username the username from the CONNECT packet (may be {@code null})
     * @param password the password from the CONNECT packet (may be {@code null})
     * @return {@code true} if the client is authenticated, {@code false} to reject
     */
    boolean authenticate(String username, String password);
}
