package ssg.legoflow.network.cluster.core;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * SPI for checking the health of cluster nodes.
 *
 * Implementations probe nodes using protocol-specific mechanisms
 * (HTTP health endpoints, gRPC health checks, TCP ping, etc.).
 */
public interface ClusterHealthChecker {

    /**
     * Checks the health of a specific node.
     *
     * @param node the node to check
     * @return a future completing with true if the node is healthy
     */
    CompletableFuture<Boolean> check(ClusterNode node);

    /**
     * Returns the default check interval for periodic health checks.
     */
    Duration defaultInterval();

    /**
     * Returns the timeout for a single health check probe.
     */
    Duration checkTimeout();

    /**
     * Creates a simple health checker with the given interval and timeout.
     *
     * @param interval the probe interval
     * @param timeout  the probe timeout
     * @return a new health checker
     */
    static ClusterHealthChecker simple(Duration interval, Duration timeout) {
        return new SimpleHealthChecker(interval, timeout);
    }

    /**
     * Default implementation using a configurable interval and timeout.
     */
    class SimpleHealthChecker implements ClusterHealthChecker {
        private final Duration interval;
        private final Duration timeout;

        SimpleHealthChecker(Duration interval, Duration timeout) {
            this.interval = interval;
            this.timeout = timeout;
        }

        @Override
        public CompletableFuture<Boolean> check(ClusterNode node) {
            // Default: always healthy (implementations override)
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public Duration defaultInterval() {
            return interval;
        }

        @Override
        public Duration checkTimeout() {
            return timeout;
        }
    }
}
