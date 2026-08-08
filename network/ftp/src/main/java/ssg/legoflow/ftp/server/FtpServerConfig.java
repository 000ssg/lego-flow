package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.security.FtpsConfig;

import java.time.Duration;

/**
 * Configuration for an FTP server.
 *
 * @since 0.1.0
 */
public final class FtpServerConfig {

    private final String host;
    private final int port;
    private final int passivePortMin;
    private final int passivePortMax;
    private final Duration sessionTimeout;
    private final int maxConnections;
    private final String serverName;
    private final FtpsConfig ftpsConfig;

    private FtpServerConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.passivePortMin = builder.passivePortMin;
        this.passivePortMax = builder.passivePortMax;
        this.sessionTimeout = builder.sessionTimeout;
        this.maxConnections = builder.maxConnections;
        this.serverName = builder.serverName;
        this.ftpsConfig = builder.ftpsConfig;
    }

    /**
     * Returns a new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a default configuration (localhost:21).
     *
     * @return default config
     */
    public static FtpServerConfig defaults() {
        return builder().build();
    }

    /** Returns the bind address. */
    public String host() { return host; }
    /** Returns the control port. */
    public int port() { return port; }
    /** Returns the minimum passive port. */
    public int passivePortMin() { return passivePortMin; }
    /** Returns the maximum passive port. */
    public int passivePortMax() { return passivePortMax; }
    /** Returns the session idle timeout. */
    public Duration sessionTimeout() { return sessionTimeout; }
    /** Returns the maximum concurrent connections. */
    public int maxConnections() { return maxConnections; }
    /** Returns the server identification name. */
    public String serverName() { return serverName; }
    /** Returns the FTPS configuration, or {@code null} if TLS is not enabled. */
    public FtpsConfig ftpsConfig() { return ftpsConfig; }
    /** Returns {@code true} if FTPS is configured. */
    public boolean isFtpsEnabled() { return ftpsConfig != null; }

    /**
     * Builder for {@link FtpServerConfig}.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private String host = "0.0.0.0";
        private int port = 21;
        private int passivePortMin = 0;
        private int passivePortMax = 0;
        private Duration sessionTimeout = Duration.ofMinutes(5);
        private int maxConnections = 100;
        private String serverName = "LegoFlow FTP Server";
        private FtpsConfig ftpsConfig;

        private Builder() {}

        /** Sets the bind address (default: 0.0.0.0). */
        public Builder host(String host) { this.host = host; return this; }
        /** Sets the control port (default: 21). */
        public Builder port(int port) { this.port = port; return this; }
        /** Sets the passive port range (default: 0 = ephemeral). */
        public Builder passivePortRange(int min, int max) {
            this.passivePortMin = min;
            this.passivePortMax = max;
            return this;
        }
        /** Sets the session idle timeout (default: 5 min). */
        public Builder sessionTimeout(Duration timeout) { this.sessionTimeout = timeout; return this; }
        /** Sets the maximum concurrent connections (default: 100). */
        public Builder maxConnections(int max) { this.maxConnections = max; return this; }
        /** Sets the server name for SYST/greeting (default: "LegoFlow FTP Server"). */
        public Builder serverName(String name) { this.serverName = name; return this; }
        /** Sets the FTPS configuration to enable TLS support. */
        public Builder ftpsConfig(FtpsConfig config) { this.ftpsConfig = config; return this; }

        /** Builds the configuration. */
        public FtpServerConfig build() { return new FtpServerConfig(this); }
    }
}
