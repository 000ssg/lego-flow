package ssg.legoflow.messaging.mqtt.broker;

/**
 * Configuration for {@link MqttBroker}.
 *
 * @param host                    the bind address
 * @param port                    the listen port
 * @param maxConnections          the maximum number of concurrent connections
 * @param maxMessageSize          the maximum message size in bytes
 * @param maxTopicLevels          the maximum number of topic levels
 * @param allowAnonymous          whether anonymous connections are allowed
 * @param requireAuthentication   whether authentication is required
 * @param sessionExpiryInterval   the default session expiry interval in seconds
 * @param maxQueuedMessages       the maximum number of queued messages per session
 * @param tlsConfig               optional TLS configuration for MQTTS (may be {@code null})
 * @param authenticator           optional client authenticator (may be {@code null})
 * @param aclChecker              optional topic-level ACL checker (may be {@code null})
 * @since 0.1.0
 */
public record MqttBrokerConfig(
        String host,
        int port,
        int maxConnections,
        int maxMessageSize,
        int maxTopicLevels,
        boolean allowAnonymous,
        boolean requireAuthentication,
        long sessionExpiryInterval,
        int maxQueuedMessages,
        MqttTlsConfig tlsConfig,
        MqttAuthenticator authenticator,
        MqttAclChecker aclChecker
) {

    /**
     * Backwards-compatible constructor without TLS, authenticator, or ACL.
     */
    public MqttBrokerConfig(String host, int port, int maxConnections, int maxMessageSize,
                             int maxTopicLevels, boolean allowAnonymous,
                             boolean requireAuthentication, long sessionExpiryInterval,
                             int maxQueuedMessages) {
        this(host, port, maxConnections, maxMessageSize, maxTopicLevels, allowAnonymous,
                requireAuthentication, sessionExpiryInterval, maxQueuedMessages, null, null, null);
    }

    /**
     * Returns a default broker configuration listening on all interfaces, port 1883.
     *
     * @return a default configuration
     */
    public static MqttBrokerConfig defaults() {
        return new MqttBrokerConfig("0.0.0.0", 1883, 1000, 268435456, 128,
                true, false, 0, 1000, null, null, null);
    }

    /**
     * Returns a minimal broker configuration suitable for testing.
     *
     * @return a minimal configuration
     */
    public static MqttBrokerConfig minimal() {
        return new MqttBrokerConfig("localhost", 0, 10, 65536, 32,
                true, false, 0, 100, null, null, null);
    }
}
