package ssg.legoflow.ftp.security;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Objects;

/**
 * TLS configuration for FTPS connections (RFC 4217).
 *
 * <p>Configures keystore (server identity), truststore (trusted CAs),
 * TLS protocol versions, and cipher suites.
 *
 * <p>Usage example:
 * <pre>{@code
 *   var config = FtpsConfig.builder()
 *       .keystorePath(Path.of("server.jks"))
 *       .keystorePassword("changeit")
 *       .mode(FtpsMode.EXPLICIT)
 *       .build();
 * }</pre>
 *
 * @since 0.1.0
 */
public final class FtpsConfig {

    private final Path keystorePath;
    private final char[] keystorePassword;
    private final String keystoreType;
    private final Path truststorePath;
    private final char[] truststorePassword;
    private final String truststoreType;
    private final String[] protocols;
    private final String[] cipherSuites;
    private final FtpsMode mode;
    private final boolean clientAuth;

    private FtpsConfig(Builder builder) {
        this.keystorePath = builder.keystorePath;
        this.keystorePassword = builder.keystorePassword;
        this.keystoreType = builder.keystoreType;
        this.truststorePath = builder.truststorePath;
        this.truststorePassword = builder.truststorePassword;
        this.truststoreType = builder.truststoreType;
        this.protocols = builder.protocols;
        this.cipherSuites = builder.cipherSuites;
        this.mode = builder.mode;
        this.clientAuth = builder.clientAuth;
    }

    /**
     * Returns a new builder for constructing an {@link FtpsConfig}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default config that trusts all certificates (for testing only).
     *
     * @return a permissive FTPS config
     */
    public static FtpsConfig trustAll() {
        return builder().mode(FtpsMode.EXPLICIT).build();
    }

    /** Returns the keystore path, or {@code null}. */
    public Path keystorePath() { return keystorePath; }
    /** Returns the keystore password. */
    public char[] keystorePassword() { return keystorePassword; }
    /** Returns the keystore type. */
    public String keystoreType() { return keystoreType; }
    /** Returns the truststore path, or {@code null}. */
    public Path truststorePath() { return truststorePath; }
    /** Returns the truststore password. */
    public char[] truststorePassword() { return truststorePassword; }
    /** Returns the truststore type. */
    public String truststoreType() { return truststoreType; }
    /** Returns the allowed TLS protocol versions. */
    public String[] protocols() { return protocols.clone(); }
    /** Returns the allowed cipher suites, or {@code null} for defaults. */
    public String[] cipherSuites() { return cipherSuites != null ? cipherSuites.clone() : null; }
    /** Returns the FTPS mode (implicit or explicit). */
    public FtpsMode mode() { return mode; }
    /** Returns whether client certificate authentication is required. */
    public boolean clientAuth() { return clientAuth; }

    /**
     * Creates an {@link SSLContext} from this configuration.
     *
     * @return the configured SSL context
     * @throws GeneralSecurityException if key/trust store initialization fails
     * @throws IOException              if key/trust store files cannot be read
     */
    public SSLContext createSslContext() throws GeneralSecurityException, IOException {
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        if (keystorePath != null) {
            KeyStore ks = KeyStore.getInstance(keystoreType);
            try (InputStream in = Files.newInputStream(keystorePath)) {
                ks.load(in, keystorePassword);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keystorePassword);
            keyManagers = kmf.getKeyManagers();
        }

        if (truststorePath != null) {
            KeyStore ts = KeyStore.getInstance(truststoreType);
            try (InputStream in = Files.newInputStream(truststorePath)) {
                ts.load(in, truststorePassword);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
            trustManagers = tmf.getTrustManagers();
        } else if (keystorePath == null) {
            // Trust-all for testing when no stores are configured
            trustManagers = new TrustManager[]{new TrustAllManager()};
        }

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keyManagers, trustManagers, null);
        return ctx;
    }

    /**
     * Creates an {@link SSLSocketFactory} from this configuration.
     *
     * @return the SSL socket factory
     * @throws GeneralSecurityException if initialization fails
     * @throws IOException              if key/trust store files cannot be read
     */
    public SSLSocketFactory createSocketFactory() throws GeneralSecurityException, IOException {
        SSLContext ctx = createSslContext();
        return ctx.getSocketFactory();
    }

    /**
     * Creates an {@link SSLServerSocketFactory} from this configuration.
     *
     * @return the SSL server socket factory
     * @throws GeneralSecurityException if initialization fails
     * @throws IOException              if key/trust store files cannot be read
     */
    public SSLServerSocketFactory createServerSocketFactory() throws GeneralSecurityException, IOException {
        SSLContext ctx = createSslContext();
        return ctx.getServerSocketFactory();
    }

    /**
     * Builder for {@link FtpsConfig}.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private Path keystorePath;
        private char[] keystorePassword = new char[0];
        private String keystoreType = "JKS";
        private Path truststorePath;
        private char[] truststorePassword = new char[0];
        private String truststoreType = "JKS";
        private String[] protocols = new String[]{"TLSv1.2", "TLSv1.3"};
        private String[] cipherSuites;
        private FtpsMode mode = FtpsMode.EXPLICIT;
        private boolean clientAuth = false;

        private Builder() {}

        /** Sets the keystore path. */
        public Builder keystorePath(Path path) { this.keystorePath = path; return this; }
        /** Sets the keystore password. */
        public Builder keystorePassword(String password) { this.keystorePassword = password.toCharArray(); return this; }
        /** Sets the keystore type (default: JKS). */
        public Builder keystoreType(String type) { this.keystoreType = type; return this; }
        /** Sets the truststore path. */
        public Builder truststorePath(Path path) { this.truststorePath = path; return this; }
        /** Sets the truststore password. */
        public Builder truststorePassword(String password) { this.truststorePassword = password.toCharArray(); return this; }
        /** Sets the truststore type (default: JKS). */
        public Builder truststoreType(String type) { this.truststoreType = type; return this; }
        /** Sets the allowed TLS protocols (default: TLSv1.2, TLSv1.3). */
        public Builder protocols(String... protocols) { this.protocols = protocols.clone(); return this; }
        /** Sets the allowed cipher suites (default: JVM defaults). */
        public Builder cipherSuites(String... suites) { this.cipherSuites = suites.clone(); return this; }
        /** Sets the FTPS mode (default: EXPLICIT). */
        public Builder mode(FtpsMode mode) { this.mode = Objects.requireNonNull(mode); return this; }
        /** Sets whether client certificate authentication is required (default: false). */
        public Builder clientAuth(boolean clientAuth) { this.clientAuth = clientAuth; return this; }

        /**
         * Builds the configuration.
         *
         * @return the immutable FTPS config
         */
        public FtpsConfig build() {
            return new FtpsConfig(this);
        }
    }

    /**
     * A trust manager that accepts all certificates — for testing only.
     */
    private static final class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            // Accept all
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            // Accept all
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }
    }
}
