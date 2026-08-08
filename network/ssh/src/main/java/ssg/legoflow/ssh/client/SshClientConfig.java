package ssg.legoflow.ssh.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SSH client configuration with preferred algorithms and timeouts.
 *
 * @since 0.1.0
 */
public final class SshClientConfig {

    private final List<String> preferredKexAlgorithms;
    private final List<String> preferredHostKeyAlgorithms;
    private final List<String> preferredCiphers;
    private final List<String> preferredMacs;
    private final List<String> preferredCompressions;
    private final Duration connectTimeout;
    private final Duration authTimeout;
    private final boolean strictHostKeyChecking;

    private SshClientConfig(Builder builder) {
        this.preferredKexAlgorithms = List.copyOf(builder.preferredKexAlgorithms);
        this.preferredHostKeyAlgorithms = List.copyOf(builder.preferredHostKeyAlgorithms);
        this.preferredCiphers = List.copyOf(builder.preferredCiphers);
        this.preferredMacs = List.copyOf(builder.preferredMacs);
        this.preferredCompressions = List.copyOf(builder.preferredCompressions);
        this.connectTimeout = builder.connectTimeout;
        this.authTimeout = builder.authTimeout;
        this.strictHostKeyChecking = builder.strictHostKeyChecking;
    }

    /** @return preferred key exchange algorithms */
    public List<String> preferredKexAlgorithms() { return preferredKexAlgorithms; }
    /** @return preferred host key algorithms */
    public List<String> preferredHostKeyAlgorithms() { return preferredHostKeyAlgorithms; }
    /** @return preferred cipher algorithms */
    public List<String> preferredCiphers() { return preferredCiphers; }
    /** @return preferred MAC algorithms */
    public List<String> preferredMacs() { return preferredMacs; }
    /** @return preferred compression algorithms */
    public List<String> preferredCompressions() { return preferredCompressions; }
    /** @return connect timeout */
    public Duration connectTimeout() { return connectTimeout; }
    /** @return auth timeout */
    public Duration authTimeout() { return authTimeout; }
    /** @return whether strict host key checking is enabled */
    public boolean strictHostKeyChecking() { return strictHostKeyChecking; }

    /**
     * Creates a default configuration.
     *
     * @return the default config
     */
    public static SshClientConfig defaults() {
        return builder().build();
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SSH client configuration.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private List<String> preferredKexAlgorithms = List.of(
                "curve25519-sha256", "ecdh-sha2-nistp256", "diffie-hellman-group16-sha512",
                "diffie-hellman-group14-sha256");
        private List<String> preferredHostKeyAlgorithms = List.of(
                "ssh-ed25519", "ecdsa-sha2-nistp256", "rsa-sha2-512", "rsa-sha2-256");
        private List<String> preferredCiphers = List.of(
                "chacha20-poly1305@openssh.com", "aes256-gcm@openssh.com",
                "aes128-gcm@openssh.com", "aes256-ctr", "aes128-ctr");
        private List<String> preferredMacs = List.of(
                "hmac-sha2-512-etm@openssh.com", "hmac-sha2-256-etm@openssh.com",
                "hmac-sha2-512", "hmac-sha2-256");
        private List<String> preferredCompressions = List.of("none", "zlib@openssh.com");
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration authTimeout = Duration.ofSeconds(30);
        private boolean strictHostKeyChecking = true;

        private Builder() {}

        public Builder preferredKexAlgorithms(List<String> algs) {
            this.preferredKexAlgorithms = new ArrayList<>(algs); return this;
        }
        public Builder preferredHostKeyAlgorithms(List<String> algs) {
            this.preferredHostKeyAlgorithms = new ArrayList<>(algs); return this;
        }
        public Builder preferredCiphers(List<String> algs) {
            this.preferredCiphers = new ArrayList<>(algs); return this;
        }
        public Builder preferredMacs(List<String> algs) {
            this.preferredMacs = new ArrayList<>(algs); return this;
        }
        public Builder preferredCompressions(List<String> algs) {
            this.preferredCompressions = new ArrayList<>(algs); return this;
        }
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = Objects.requireNonNull(timeout); return this;
        }
        public Builder authTimeout(Duration timeout) {
            this.authTimeout = Objects.requireNonNull(timeout); return this;
        }
        public Builder strictHostKeyChecking(boolean strict) {
            this.strictHostKeyChecking = strict; return this;
        }

        public SshClientConfig build() {
            return new SshClientConfig(this);
        }
    }
}
