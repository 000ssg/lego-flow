package ssg.legoflow.ssh.server;

import java.time.Duration;
import java.util.List;

/**
 * SSH server configuration.
 *
 * @since 0.1.0
 */
public final class SshServerConfig {

    private final int port;
    private final String bindAddress;
    private final Duration authTimeout;
    private final int maxAuthAttempts;
    private final int maxConcurrentConnections;
    private final List<String> preferredKexAlgorithms;
    private final List<String> preferredHostKeyAlgorithms;
    private final List<String> preferredCiphers;
    private final List<String> preferredMacs;

    private SshServerConfig(Builder builder) {
        this.port = builder.port;
        this.bindAddress = builder.bindAddress;
        this.authTimeout = builder.authTimeout;
        this.maxAuthAttempts = builder.maxAuthAttempts;
        this.maxConcurrentConnections = builder.maxConcurrentConnections;
        this.preferredKexAlgorithms = List.copyOf(builder.preferredKexAlgorithms);
        this.preferredHostKeyAlgorithms = List.copyOf(builder.preferredHostKeyAlgorithms);
        this.preferredCiphers = List.copyOf(builder.preferredCiphers);
        this.preferredMacs = List.copyOf(builder.preferredMacs);
    }

    public int port() { return port; }
    public String bindAddress() { return bindAddress; }
    public Duration authTimeout() { return authTimeout; }
    public int maxAuthAttempts() { return maxAuthAttempts; }
    public int maxConcurrentConnections() { return maxConcurrentConnections; }
    public List<String> preferredKexAlgorithms() { return preferredKexAlgorithms; }
    public List<String> preferredHostKeyAlgorithms() { return preferredHostKeyAlgorithms; }
    public List<String> preferredCiphers() { return preferredCiphers; }
    public List<String> preferredMacs() { return preferredMacs; }

    public static Builder builder() { return new Builder(); }

    public static SshServerConfig defaults() { return builder().build(); }

    /**
     * Builder for server configuration.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private int port = 22;
        private String bindAddress = "0.0.0.0";
        private Duration authTimeout = Duration.ofSeconds(30);
        private int maxAuthAttempts = 6;
        private int maxConcurrentConnections = 100;
        private List<String> preferredKexAlgorithms = List.of(
            "curve25519-sha256", "curve25519-sha256@libssh.org", "curve25519-sha256@openssh.com",
            "ecdh-sha2-nistp256", "ecdh-sha2-nistp256@openssh.com",
            "ecdh-sha2-nistp384", "ecdh-sha2-nistp384@openssh.com",
            "ecdh-sha2-nistp521", "ecdh-sha2-nistp521@openssh.com",
            "diffie-hellman-group16-sha512",
            "diffie-hellman-group14-sha256"
        );
        private List<String> preferredHostKeyAlgorithms = List.of(
            "ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384",
            "rsa-sha2-512", "rsa-sha2-256"
        );
        private List<String> preferredCiphers = List.of("aes256-ctr", "aes128-ctr");
        private List<String> preferredMacs = List.of("hmac-sha2-256", "hmac-sha2-512");

        private Builder() {}

        public Builder port(int port) { this.port = port; return this; }
        public Builder bindAddress(String addr) { this.bindAddress = addr; return this; }
        public Builder authTimeout(Duration timeout) { this.authTimeout = timeout; return this; }
        public Builder maxAuthAttempts(int max) { this.maxAuthAttempts = max; return this; }
        public Builder maxConcurrentConnections(int max) { this.maxConcurrentConnections = max; return this; }
        public Builder preferredKexAlgorithms(List<String> algorithms) { this.preferredKexAlgorithms = algorithms; return this; }
        public Builder preferredHostKeyAlgorithms(List<String> algorithms) { this.preferredHostKeyAlgorithms = algorithms; return this; }
        public Builder preferredCiphers(List<String> ciphers) { this.preferredCiphers = ciphers; return this; }
        public Builder preferredMacs(List<String> macs) { this.preferredMacs = macs; return this; }

        public SshServerConfig build() { return new SshServerConfig(this); }
    }
}
