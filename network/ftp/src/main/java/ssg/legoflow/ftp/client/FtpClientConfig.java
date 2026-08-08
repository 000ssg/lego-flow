package ssg.legoflow.ftp.client;

import java.time.Duration;

/**
 * Configuration for an FTP client.
 *
 * @since 0.1.0
 */
public final class FtpClientConfig {

    private final Duration connectTimeout;
    private final Duration soTimeout;
    private final int bufferSize;
    private final boolean passiveMode;
    private final boolean autoDetectBinary;
    private final String defaultControlEncoding;

    private FtpClientConfig(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.soTimeout = builder.soTimeout;
        this.bufferSize = builder.bufferSize;
        this.passiveMode = builder.passiveMode;
        this.autoDetectBinary = builder.autoDetectBinary;
        this.defaultControlEncoding = builder.defaultControlEncoding;
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
     * Returns a default configuration.
     *
     * @return default config
     */
    public static FtpClientConfig defaults() {
        return builder().build();
    }

    /** Returns the connect timeout. */
    public Duration connectTimeout() { return connectTimeout; }
    /** Returns the socket read timeout. */
    public Duration soTimeout() { return soTimeout; }
    /** Returns the transfer buffer size. */
    public int bufferSize() { return bufferSize; }
    /** Returns whether passive mode is the default. */
    public boolean passiveMode() { return passiveMode; }
    /** Returns whether binary mode should be auto-detected from file extension. */
    public boolean autoDetectBinary() { return autoDetectBinary; }
    /** Returns the control channel encoding. */
    public String defaultControlEncoding() { return defaultControlEncoding; }

    /**
     * Builder for {@link FtpClientConfig}.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration soTimeout = Duration.ofSeconds(30);
        private int bufferSize = 8192;
        private boolean passiveMode = true;
        private boolean autoDetectBinary = true;
        private String defaultControlEncoding = "UTF-8";

        private Builder() {}

        /** Sets the connect timeout (default: 10s). */
        public Builder connectTimeout(Duration timeout) { this.connectTimeout = timeout; return this; }
        /** Sets the socket read timeout (default: 30s). */
        public Builder soTimeout(Duration timeout) { this.soTimeout = timeout; return this; }
        /** Sets the transfer buffer size (default: 8192). */
        public Builder bufferSize(int size) { this.bufferSize = size; return this; }
        /** Sets whether passive mode is the default (default: true). */
        public Builder passiveMode(boolean passive) { this.passiveMode = passive; return this; }
        /** Sets whether to auto-detect binary mode from file extension (default: true). */
        public Builder autoDetectBinary(boolean detect) { this.autoDetectBinary = detect; return this; }
        /** Sets the control channel encoding (default: UTF-8). */
        public Builder defaultControlEncoding(String encoding) { this.defaultControlEncoding = encoding; return this; }

        /** Builds the configuration. */
        public FtpClientConfig build() { return new FtpClientConfig(this); }
    }
}
