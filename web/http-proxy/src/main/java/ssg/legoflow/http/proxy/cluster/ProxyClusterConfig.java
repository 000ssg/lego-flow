package ssg.legoflow.http.proxy.cluster;

import ssg.legoflow.http.proxy.reverse.BackendServer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
/**
 * Configuration for the proxy cluster backend group.
 *
 * <p>Defines the set of backend servers, health check parameters,
 * and failover behavior for a cluster of backend web servers.
 *
 * @param backends          the initial list of backend servers
 * @param healthCheckPath   HTTP path for health probes (default: /health)
 * @param healthInterval    time between health checks
 * @param unhealthyThreshold consecutive failures before marking unhealthy
 * @param recoveryThreshold  consecutive successes before marking healthy again
 * @since 0.2.0
 */
public record ProxyClusterConfig(
        List<BackendServer> backends,
        String healthCheckPath,
        Duration healthInterval,
        int unhealthyThreshold,
        int recoveryThreshold
) {

    private static final String DEFAULT_HEALTH_PATH = "/health";
    private static final Duration DEFAULT_HEALTH_INTERVAL = Duration.ofSeconds(10);
    private static final int DEFAULT_UNHEALTHY_THRESHOLD = 3;
    private static final int DEFAULT_RECOVERY_THRESHOLD = 2;

    /**
     * Creates a builder with defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ProxyClusterConfig.
     */
    public static class Builder {
        private List<BackendServer> backends = List.of();
        private String healthCheckPath = DEFAULT_HEALTH_PATH;
        private Duration healthInterval = DEFAULT_HEALTH_INTERVAL;
        private int unhealthyThreshold = DEFAULT_UNHEALTHY_THRESHOLD;
        private int recoveryThreshold = DEFAULT_RECOVERY_THRESHOLD;

        public Builder backends(List<BackendServer> backends) {
            this.backends = Objects.requireNonNull(backends);
            return this;
        }

        public Builder healthCheckPath(String path) {
            this.healthCheckPath = Objects.requireNonNull(path);
            return this;
        }

        public Builder healthInterval(Duration interval) {
            this.healthInterval = Objects.requireNonNull(interval);
            return this;
        }

        public Builder unhealthyThreshold(int threshold) {
            if (threshold < 1) throw new IllegalArgumentException("threshold must be >= 1");
            this.unhealthyThreshold = threshold;
            return this;
        }

        public Builder recoveryThreshold(int threshold) {
            if (threshold < 1) throw new IllegalArgumentException("threshold must be >= 1");
            this.recoveryThreshold = threshold;
            return this;
        }

        public ProxyClusterConfig build() {
            return new ProxyClusterConfig(backends, healthCheckPath, healthInterval,
                    unhealthyThreshold, recoveryThreshold);
        }
    }
}
