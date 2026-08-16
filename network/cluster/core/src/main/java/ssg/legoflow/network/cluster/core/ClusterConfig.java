package ssg.legoflow.network.cluster.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for cluster behavior.
 *
 * Controls discovery, heartbeat intervals, failure detection timeouts,
 * and transport settings.
 */
public final class ClusterConfig {

    public static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(1);
    public static final int DEFAULT_HEARTBEAT_FAILURE_THRESHOLD = 3;
    public static final Duration DEFAULT_JOIN_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_LEAVE_TIMEOUT = Duration.ofSeconds(3);

    private final String name;
    private final Duration heartbeatInterval;
    private final int heartbeatFailureThreshold;
    private final Duration joinTimeout;
    private final Duration leaveTimeout;

    private ClusterConfig(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.heartbeatInterval = Objects.requireNonNull(builder.heartbeatInterval);
        this.heartbeatFailureThreshold = builder.heartbeatFailureThreshold;
        this.joinTimeout = Objects.requireNonNull(builder.joinTimeout);
        this.leaveTimeout = Objects.requireNonNull(builder.leaveTimeout);
    }

    /**
     * Creates a default configuration for the given cluster name.
     *
     * @param name the cluster name
     * @return a default configuration
     */
    public static ClusterConfig defaultsFor(String name) {
        return builder().name(name).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The human-readable cluster name.
     */
    public String name() {
        return name;
    }

    /**
     * Interval between heartbeat broadcasts.
     */
    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * Number of missed heartbeats before a node is marked as FAILED.
     */
    public int heartbeatFailureThreshold() {
        return heartbeatFailureThreshold;
    }

    /**
     * Maximum time to wait for a node to join the cluster.
     */
    public Duration joinTimeout() {
        return joinTimeout;
    }

    /**
     * Maximum time to wait for a graceful leave.
     */
    public Duration leaveTimeout() {
        return leaveTimeout;
    }

    /**
     * Timeout after which a suspect node is marked as failed.
     * Derived from heartbeatInterval * heartbeatFailureThreshold.
     */
    public Duration failureTimeout() {
        return heartbeatInterval.multipliedBy(heartbeatFailureThreshold);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterConfig that = (ClusterConfig) o;
        return heartbeatFailureThreshold == that.heartbeatFailureThreshold
                && name.equals(that.name)
                && heartbeatInterval.equals(that.heartbeatInterval)
                && joinTimeout.equals(that.joinTimeout)
                && leaveTimeout.equals(that.leaveTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, heartbeatInterval, heartbeatFailureThreshold,
                joinTimeout, leaveTimeout);
    }

    @Override
    public String toString() {
        return "ClusterConfig{name='" + name + "', heartbeat=" + heartbeatInterval
                + ", threshold=" + heartbeatFailureThreshold + '}';
    }

    public static class Builder {
        private String name;
        private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
        private int heartbeatFailureThreshold = DEFAULT_HEARTBEAT_FAILURE_THRESHOLD;
        private Duration joinTimeout = DEFAULT_JOIN_TIMEOUT;
        private Duration leaveTimeout = DEFAULT_LEAVE_TIMEOUT;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder heartbeatInterval(Duration interval) {
            if (interval.isNegative() || interval.isZero()) {
                throw new IllegalArgumentException("heartbeatInterval must be positive");
            }
            this.heartbeatInterval = interval;
            return this;
        }

        public Builder heartbeatFailureThreshold(int threshold) {
            if (threshold < 1) {
                throw new IllegalArgumentException("heartbeatFailureThreshold must be >= 1");
            }
            this.heartbeatFailureThreshold = threshold;
            return this;
        }

        public Builder joinTimeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("joinTimeout must be positive");
            }
            this.joinTimeout = timeout;
            return this;
        }

        public Builder leaveTimeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("leaveTimeout must be positive");
            }
            this.leaveTimeout = timeout;
            return this;
        }

        public ClusterConfig build() {
            return new ClusterConfig(this);
        }
    }
}
