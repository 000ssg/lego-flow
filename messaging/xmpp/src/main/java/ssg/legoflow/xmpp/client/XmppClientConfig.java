package ssg.legoflow.xmpp.client;

import ssg.legoflow.xmpp.auth.SaslMechanism;
import java.time.Duration;
import java.util.Objects;
/**
 * Configuration for an XMPP client connection.
 *
 * @since 0.1.0
 */
public class XmppClientConfig {

    private final String host;
    private final int port;
    private final String domain;
    private final Duration connectTimeout;
    private final Duration keepAliveInterval;
    private final boolean enableTls;
    private final SaslMechanism saslMechanism;

    private XmppClientConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.domain = builder.domain;
        this.connectTimeout = builder.connectTimeout;
        this.keepAliveInterval = builder.keepAliveInterval;
        this.enableTls = builder.enableTls;
        this.saslMechanism = builder.saslMechanism;
    }

    /**
     * Creates a default configuration for the given host and domain.
     *
     * @param host   the server hostname
     * @param domain the XMPP domain
     * @return a new default configuration
     */
    public static XmppClientConfig defaults(String host, String domain) {
        return new Builder(host, domain).build();
    }

    /**
     * Creates a new builder.
     *
     * @param host   the server hostname
     * @param domain the XMPP domain
     * @return a new builder
     */
    public static Builder builder(String host, String domain) {
        return new Builder(host, domain);
    }

    /** @return the server hostname */
    public String host() { return host; }

    /** @return the server port */
    public int port() { return port; }

    /** @return the XMPP domain */
    public String domain() { return domain; }

    /** @return the connection timeout */
    public Duration connectTimeout() { return connectTimeout; }

    /** @return the keep-alive interval */
    public Duration keepAliveInterval() { return keepAliveInterval; }

    /** @return whether TLS is enabled */
    public boolean enableTls() { return enableTls; }

    /** @return the SASL mechanism */
    public SaslMechanism saslMechanism() { return saslMechanism; }

    @Override
    public String toString() {
        return "XmppClientConfig{host=" + host + ", port=" + port +
                ", domain=" + domain + ", tls=" + enableTls +
                ", sasl=" + saslMechanism + "}";
    }

    /**
     * Builder for XmppClientConfig.
     *
     * @since 0.1.0
     */
    public static class Builder {
        private final String host;
        private final String domain;
        private int port = 5222;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration keepAliveInterval = Duration.ofMinutes(5);
        private boolean enableTls = true;
        private SaslMechanism saslMechanism = SaslMechanism.PLAIN;

        private Builder(String host, String domain) {
            this.host = Objects.requireNonNull(host, "host must not be null");
            this.domain = Objects.requireNonNull(domain, "domain must not be null");
        }

        /**
         * Sets the server port.
         *
         * @param port the port number
         * @return this builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the connection timeout.
         *
         * @param connectTimeout the timeout duration
         * @return this builder
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * Sets the keep-alive interval.
         *
         * @param keepAliveInterval the interval duration
         * @return this builder
         */
        public Builder keepAliveInterval(Duration keepAliveInterval) {
            this.keepAliveInterval = keepAliveInterval;
            return this;
        }

        /**
         * Sets whether TLS is enabled.
         *
         * @param enableTls true to enable TLS
         * @return this builder
         */
        public Builder enableTls(boolean enableTls) {
            this.enableTls = enableTls;
            return this;
        }

        /**
         * Sets the SASL mechanism.
         *
         * @param saslMechanism the mechanism
         * @return this builder
         */
        public Builder saslMechanism(SaslMechanism saslMechanism) {
            this.saslMechanism = saslMechanism;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the new configuration
         */
        public XmppClientConfig build() {
            return new XmppClientConfig(this);
        }
    }
}
