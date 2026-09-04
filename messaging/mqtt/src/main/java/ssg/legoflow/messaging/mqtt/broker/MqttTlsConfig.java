package ssg.legoflow.messaging.mqtt.broker;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import java.util.Objects;

/**
 * TLS configuration for MQTT connections (MQTTS on port 8883).
 *
 * <p>Mirrors the approach used by the HTTP module's {@code SslConfig},
 * providing keystore/truststore paths, protocol versions, and cipher suites.
 * Creates {@link SSLContext} and {@link SSLEngine} instances for both
 * client and server modes.
 *
 * @since 0.1.0
 */
public final class MqttTlsConfig {

    private final String keystorePath;
    private final String keystorePassword;
    private final String truststorePath;
    private final String truststorePassword;
    private final List<String> protocols;
    private final List<String> cipherSuites;
    private final javax.net.ssl.SSLContext sslContext;

    private MqttTlsConfig(Builder builder) {
        this.keystorePath = builder.keystorePath;
        this.keystorePassword = builder.keystorePassword;
        this.truststorePath = builder.truststorePath;
        this.truststorePassword = builder.truststorePassword;
        this.protocols = List.copyOf(builder.protocols);
        this.cipherSuites = List.copyOf(builder.cipherSuites);
        this.sslContext = builder.sslContext;
    }

    /** Returns the keystore file path. */
    public String keystorePath() { return keystorePath; }

    /** Returns the keystore password. */
    public String keystorePassword() { return keystorePassword; }

    /** Returns the truststore file path, or {@code null} if not set. */
    public String truststorePath() { return truststorePath; }

    /** Returns the truststore password, or {@code null} if not set. */
    public String truststorePassword() { return truststorePassword; }

    /** Returns the enabled TLS protocol versions. */
    public List<String> protocols() { return protocols; }

    /** Returns the enabled cipher suites (empty list means JVM defaults). */
    public List<String> cipherSuites() { return cipherSuites; }

    /** Returns the pre-built SSL context, or {@code null} if using file-based keystores. */
    public javax.net.ssl.SSLContext sslContext() { return sslContext; }

    /**
     * Creates an {@link SSLContext} from this configuration.
     *
     * <p>If a context was provided via {@link Builder#sslContext(SSLContext)}, it is returned directly.
     * Otherwise, file-based keystores are loaded.</p>
     *
     * @return the configured SSL context
     * @throws GeneralSecurityException if keystore/truststore loading fails
     * @throws IOException              if reading keystore/truststore files fails
     * @throws IllegalStateException    if neither keystore nor SSL context is configured
     */
    public SSLContext createSslContext() throws GeneralSecurityException, IOException {
        if (sslContext != null) {
            return sslContext;
        }
        if (keystorePath == null || keystorePassword == null) {
            throw new IllegalStateException("Keystore path and password, or pre-built SSLContext, are required");
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var fis = new FileInputStream(keystorePath)) {
            ks.load(fis, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keystorePassword.toCharArray());

        TrustManagerFactory tmf = null;
        if (truststorePath != null) {
            KeyStore ts = KeyStore.getInstance("PKCS12");
            try (var fis = new FileInputStream(truststorePath)) {
                ts.load(fis, truststorePassword != null ? truststorePassword.toCharArray() : null);
            }
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
        }

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(),
                tmf != null ? tmf.getTrustManagers() : null, null);
        return ctx;
    }

    /**
     * Creates an {@link SSLEngine} configured for server mode.
     *
     * @param sslContext the SSL context
     * @return a server-mode SSL engine
     */
    public SSLEngine createServerEngine(SSLContext sslContext) {
        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        configureEngine(engine);
        return engine;
    }

    /**
     * Creates an {@link SSLEngine} configured for client mode.
     *
     * @param sslContext the SSL context
     * @param peerHost   the peer hostname
     * @param peerPort   the peer port
     * @return a client-mode SSL engine
     */
    public SSLEngine createClientEngine(SSLContext sslContext, String peerHost, int peerPort) {
        SSLEngine engine = sslContext.createSSLEngine(peerHost, peerPort);
        engine.setUseClientMode(true);
        configureEngine(engine);
        return engine;
    }

    private void configureEngine(SSLEngine engine) {
        if (!protocols.isEmpty()) {
            engine.setEnabledProtocols(protocols.toArray(String[]::new));
        }
        if (!cipherSuites.isEmpty()) {
            engine.setEnabledCipherSuites(cipherSuites.toArray(String[]::new));
        }
    }

    /**
     * Creates a new builder with default TLS settings.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link MqttTlsConfig}.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private String keystorePath;
        private String keystorePassword;
        private String truststorePath;
        private String truststorePassword;
        private List<String> protocols = List.of("TLSv1.3", "TLSv1.2");
        private List<String> cipherSuites = List.of();
        private javax.net.ssl.SSLContext sslContext;

        /** Sets the keystore file path. */
        public Builder keystorePath(String keystorePath) {
            this.keystorePath = Objects.requireNonNull(keystorePath);
            return this;
        }

        /** Sets the keystore password. */
        public Builder keystorePassword(String keystorePassword) {
            this.keystorePassword = Objects.requireNonNull(keystorePassword);
            return this;
        }

        /** Sets the truststore file path. */
        public Builder truststorePath(String truststorePath) {
            this.truststorePath = truststorePath;
            return this;
        }

        /** Sets the truststore password. */
        public Builder truststorePassword(String truststorePassword) {
            this.truststorePassword = truststorePassword;
            return this;
        }

        /** Sets the enabled TLS protocol versions. */
        public Builder protocols(List<String> protocols) {
            this.protocols = Objects.requireNonNull(protocols);
            return this;
        }

        /** Sets the enabled cipher suites. */
        public Builder cipherSuites(List<String> cipherSuites) {
            this.cipherSuites = Objects.requireNonNull(cipherSuites);
            return this;
        }

        /**
         * Sets a pre-built SSL context (e.g. from in-memory certificates).
         * When set, {@link #createSslContext()} returns this directly without loading files.
         */
        public Builder sslContext(javax.net.ssl.SSLContext sslContext) {
            this.sslContext = Objects.requireNonNull(sslContext);
            return this;
        }

        /**
         * Builds the TLS configuration.
         *
         * @return a new immutable TLS configuration
         * @throws IllegalStateException if neither keystore nor SSLContext is provided
         */
        public MqttTlsConfig build() {
            if (sslContext == null && (keystorePath == null || keystorePassword == null)) {
                throw new IllegalStateException("Keystore path and password, or pre-built SSLContext, are required");
            }
            return new MqttTlsConfig(this);
        }
    }
}
