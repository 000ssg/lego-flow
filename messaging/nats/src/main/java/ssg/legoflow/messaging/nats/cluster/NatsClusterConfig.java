package ssg.legoflow.messaging.nats.cluster;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the NATS cluster bus.
 *
 * <p>Defines the NATS server connections, cluster identity, and
 * messaging semantics for distributed cluster communication.
 *
 * @param serverUrl       NATS server URL (e.g., "nats://localhost:4222")
 * @param clusterId       logical cluster identifier
 * @param nodeId          unique node identifier within the cluster
 * @param heartbeatInterval interval between health check heartbeats
 * @param requestTimeout  timeout for request-reply patterns
 * @param maxPayloadBytes maximum payload size in bytes
 * @since 0.2.0
 */
public record NatsClusterConfig(
        String serverUrl,
        String clusterId,
        String nodeId,
        Duration heartbeatInterval,
        Duration requestTimeout,
        int maxPayloadBytes
) {
    private static final String DEFAULT_SERVER_URL = "nats://localhost:4222";
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_MAX_PAYLOAD = 1024 * 1024;

    /**
     * Creates a builder with defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for NatsClusterConfig.
     */
    public static class Builder {
        private String serverUrl = DEFAULT_SERVER_URL;
        private String clusterId = "default";
        private String nodeId = java.util.UUID.randomUUID().toString();
        private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private int maxPayloadBytes = DEFAULT_MAX_PAYLOAD;

        public Builder serverUrl(String url) {
            this.serverUrl = Objects.requireNonNull(url);
            return this;
        }

        public Builder clusterId(String id) {
            this.clusterId = Objects.requireNonNull(id);
            return this;
        }

        public Builder nodeId(String id) {
            this.nodeId = Objects.requireNonNull(id);
            return this;
        }

        public Builder heartbeatInterval(Duration interval) {
            this.heartbeatInterval = Objects.requireNonNull(interval);
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = Objects.requireNonNull(timeout);
            return this;
        }

        public Builder maxPayloadBytes(int bytes) {
            if (bytes <= 0) throw new IllegalArgumentException("maxPayloadBytes must be > 0");
            this.maxPayloadBytes = bytes;
            return this;
        }

        public NatsClusterConfig build() {
            return new NatsClusterConfig(serverUrl, clusterId, nodeId,
                    heartbeatInterval, requestTimeout, maxPayloadBytes);
        }
    }
}
