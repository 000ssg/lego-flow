package ssg.legoflow.service.cluster.coordination;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for connecting to an etcd cluster.
 *
 * @param endpoints     etcd server endpoints
 * @param dialTimeout   initial connection timeout
 * @param requestTimeout per-request timeout
 * @param username      authentication username (null for unauthenticated)
 * @param password      authentication password (null for unauthenticated)
 * @since 0.2.0
 */
public record EtcdConfig(
        List<InetSocketAddress> endpoints,
        Duration dialTimeout,
        Duration requestTimeout,
        String username,
        String password
) {
    public static final Duration DEFAULT_DIAL_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Builder for EtcdConfig.
     *
     * @since 0.2.0
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<InetSocketAddress> endpoints = List.of(
                new InetSocketAddress("localhost", 2379));
        private Duration dialTimeout = DEFAULT_DIAL_TIMEOUT;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private String username;
        private String password;

        public Builder endpoints(List<InetSocketAddress> endpoints) {
            this.endpoints = Objects.requireNonNull(endpoints);
            return this;
        }

        public Builder dialTimeout(Duration dialTimeout) {
            this.dialTimeout = Objects.requireNonNull(dialTimeout);
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = Objects.requireNonNull(requestTimeout);
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public EtcdConfig build() {
            if (endpoints.isEmpty())
                throw new IllegalArgumentException("endpoints must not be empty");
            return new EtcdConfig(endpoints, dialTimeout, requestTimeout, username, password);
        }
    }
}
