package ssg.legoflow.email.smtp.client;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the SMTP client.
 *
 * <p>Encapsulates connection parameters including host, port, TLS mode,
 * authentication credentials, and timeouts.
 *
 * @since 1.0.0
 */
public final class SmtpClientConfig {

    /** TLS mode for the connection. */
    public enum TlsMode {
        /** No TLS (plaintext). */
        NONE,
        /** STARTTLS upgrade after initial plaintext connection. */
        STARTTLS,
        /** Implicit TLS (connect with TLS from start). */
        IMPLICIT
    }

    private final String host;
    private final int port;
    private final TlsMode tlsMode;
    private final SSLContext sslContext;
    private final String username;
    private final String password;
    private final String authMechanism;
    private final String localHostname;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final boolean pipelining;

    private SmtpClientConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.tlsMode = builder.tlsMode;
        this.sslContext = builder.sslContext;
        this.username = builder.username;
        this.password = builder.password;
        this.authMechanism = builder.authMechanism;
        this.localHostname = builder.localHostname;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.pipelining = builder.pipelining;
    }

    /**
     * Returns a new builder.
     *
     * @param host the server hostname
     * @param port the server port
     * @return the builder
     */
    public static Builder builder(String host, int port) {
        return new Builder(host, port);
    }

    /** Returns the server hostname. */
    public String host() { return host; }
    /** Returns the server port. */
    public int port() { return port; }
    /** Returns the TLS mode. */
    public TlsMode tlsMode() { return tlsMode; }
    /** Returns the SSL context (may be {@code null}). */
    public SSLContext sslContext() { return sslContext; }
    /** Returns the authentication username (may be {@code null}). */
    public String username() { return username; }
    /** Returns the authentication password (may be {@code null}). */
    public String password() { return password; }
    /** Returns the preferred auth mechanism (may be {@code null} for auto-select). */
    public String authMechanism() { return authMechanism; }
    /** Returns the local hostname for EHLO. */
    public String localHostname() { return localHostname; }
    /** Returns the connect timeout. */
    public Duration connectTimeout() { return connectTimeout; }
    /** Returns the read timeout. */
    public Duration readTimeout() { return readTimeout; }
    /** Returns whether pipelining is enabled. */
    public boolean pipelining() { return pipelining; }

    /**
     * Returns whether authentication credentials are configured.
     *
     * @return true if username and password are set
     */
    public boolean hasAuth() {
        return username != null && password != null;
    }

    /**
     * Builder for {@link SmtpClientConfig}.
     */
    public static final class Builder {
        private final String host;
        private final int port;
        private TlsMode tlsMode = TlsMode.NONE;
        private SSLContext sslContext;
        private String username;
        private String password;
        private String authMechanism;
        private String localHostname = "localhost";
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration readTimeout = Duration.ofSeconds(60);
        private boolean pipelining = true;

        private Builder(String host, int port) {
            this.host = Objects.requireNonNull(host, "host");
            this.port = port;
        }

        /** Sets the TLS mode. */
        public Builder tlsMode(TlsMode tlsMode) {
            this.tlsMode = tlsMode;
            return this;
        }

        /** Sets the SSL context for TLS. */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /** Sets authentication credentials. */
        public Builder auth(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        /** Sets the preferred authentication mechanism. */
        public Builder authMechanism(String mechanism) {
            this.authMechanism = mechanism;
            return this;
        }

        /** Sets the local hostname for EHLO. */
        public Builder localHostname(String hostname) {
            this.localHostname = hostname;
            return this;
        }

        /** Sets the connect timeout. */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /** Sets the read timeout. */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /** Sets whether pipelining is enabled. */
        public Builder pipelining(boolean pipelining) {
            this.pipelining = pipelining;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the client config
         */
        public SmtpClientConfig build() {
            return new SmtpClientConfig(this);
        }
    }
}
