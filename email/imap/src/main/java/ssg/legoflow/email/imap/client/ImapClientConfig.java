package ssg.legoflow.email.imap.client;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for {@link ImapClient} with a fluent builder API.
 *
 * @since 1.0.0
 */
public final class ImapClientConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean useTls;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration idleTimeout;

    private ImapClientConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password;
        this.useTls = builder.useTls;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.idleTimeout = builder.idleTimeout;
    }

    /** Returns the server hostname. */
    public String host() { return host; }

    /** Returns the server port. */
    public int port() { return port; }

    /** Returns the username for LOGIN. */
    public String username() { return username; }

    /** Returns the password for LOGIN. */
    public String password() { return password; }

    /** Returns whether TLS is enabled. */
    public boolean useTls() { return useTls; }

    /** Returns the connection timeout. */
    public Duration connectTimeout() { return connectTimeout; }

    /** Returns the read timeout. */
    public Duration readTimeout() { return readTimeout; }

    /** Returns the IDLE timeout before re-issuing IDLE. */
    public Duration idleTimeout() { return idleTimeout; }

    /**
     * Creates a new builder.
     *
     * @param host the server hostname
     * @param port the server port
     * @return the builder
     */
    public static Builder builder(String host, int port) {
        return new Builder(host, port);
    }

    /**
     * Fluent builder for {@link ImapClientConfig}.
     */
    public static final class Builder {
        private final String host;
        private final int port;
        private String username;
        private String password;
        private boolean useTls = false;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration idleTimeout = Duration.ofMinutes(25);

        private Builder(String host, int port) {
            this.host = Objects.requireNonNull(host);
            this.port = port;
        }

        /** Sets the login credentials. */
        public Builder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        /** Enables or disables TLS. */
        public Builder useTls(boolean useTls) {
            this.useTls = useTls;
            return this;
        }

        /** Sets the connection timeout. */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /** Sets the read timeout. */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /** Sets the IDLE timeout. */
        public Builder idleTimeout(Duration timeout) {
            this.idleTimeout = timeout;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the configuration
         */
        public ImapClientConfig build() {
            return new ImapClientConfig(this);
        }
    }
}
