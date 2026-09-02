package ssg.legoflow.messaging.amqp.container;

import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.sasl.SaslAuthenticator;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuration for {@link AmqpContainer} with vendor simulation support.
 *
 * <p>Uses a builder API with mode-aware defaults. When {@link ContainerMode} is set,
 * the builder pre-fills SASL, channel-max, idle timeout, and mechanism settings
 * matching that broker's behavior.
 *
 * @since 0.1.0
 */
public final class ContainerConfig {

    private final String containerId;
    private final String host;
    private final int port;
    private final int maxFrameSize;
    private final int channelMax;
    private final long idleTimeout;
    private final boolean requireSasl;
    private final SaslAuthenticator authenticator;
    private final ContainerMode mode;
    private final boolean proto0Accepted;
    private final boolean authzidMustBeEmpty;

    private ContainerConfig(Builder builder) {
        this.containerId = builder.containerId;
        this.host = builder.host;
        this.port = builder.port;
        this.maxFrameSize = builder.maxFrameSize;
        this.channelMax = builder.channelMax;
        this.idleTimeout = builder.idleTimeout;
        this.requireSasl = builder.requireSasl;
        this.authenticator = builder.authenticator;
        this.mode = builder.mode;
        this.proto0Accepted = builder.proto0Accepted;
        this.authzidMustBeEmpty = builder.authzidMustBeEmpty;
    }

    /** Returns the container identifier. */
    public String containerId() { return containerId; }

    /** Returns the bind address. */
    public String host() { return host; }

    /** Returns the listen port. */
    public int port() { return port; }

    /** Returns the maximum frame size. */
    public int maxFrameSize() { return maxFrameSize; }

    /** Returns the maximum channel number. */
    public int channelMax() { return channelMax; }

    /** Returns the idle timeout in milliseconds. */
    public long idleTimeout() { return idleTimeout; }

    /** Returns whether SASL authentication is required. */
    public boolean requireSasl() { return requireSasl; }

    /** Returns the SASL authenticator. */
    public SaslAuthenticator authenticator() { return authenticator; }

    /** Returns the vendor simulation mode. */
    public ContainerMode mode() { return mode; }

    /** Returns whether proto-0 header exchange is accepted. */
    public boolean proto0Accepted() { return proto0Accepted; }

    /** Returns whether authzid in sasl-init must be empty. */
    public boolean authzidMustBeEmpty() { return authzidMustBeEmpty; }

    /**
     * Returns a default configuration in STANDARD mode (least restrictive).
     */
    public static ContainerConfig defaults() {
        return builder().build();
    }

    /**
     * Returns a configuration for testing with SASL authentication.
     */
    public static ContainerConfig withSasl(SaslAuthenticator authenticator) {
        return builder().authenticator(authenticator).build();
    }

    /**
     * Returns a configuration simulating the specified broker.
     */
    public static ContainerConfig forMode(ContainerMode mode) {
        return builder().mode(mode).build();
    }

    /**
     * Creates a builder with STANDARD mode defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ContainerConfig}.
     */
    public static final class Builder {
        private String containerId = "lego-flow-amqp-" + UUID.randomUUID().toString().substring(0, 8);
        private String host = "localhost";
        private int port = 0;
        private int maxFrameSize = AmqpConstants.DEFAULT_MAX_FRAME_SIZE;
        private int channelMax = AmqpConstants.DEFAULT_CHANNEL_MAX;
        private long idleTimeout = 0;
        private boolean requireSasl = false;
        private SaslAuthenticator authenticator;
        private ContainerMode mode = ContainerMode.STANDARD;
        private boolean proto0Accepted = true;
        private boolean authzidMustBeEmpty = false;

        public Builder containerId(String containerId) { this.containerId = Objects.requireNonNull(containerId); return this; }
        public Builder host(String host) { this.host = Objects.requireNonNull(host); return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder maxFrameSize(int maxFrameSize) { this.maxFrameSize = maxFrameSize; return this; }
        public Builder channelMax(int channelMax) { this.channelMax = channelMax; return this; }
        public Builder idleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; return this; }
        public Builder requireSasl(boolean requireSasl) { this.requireSasl = requireSasl; return this; }
        public Builder authenticator(SaslAuthenticator authenticator) { this.authenticator = authenticator; return this; }

        /**
         * Sets the vendor simulation mode. This pre-fills mode-specific defaults
         * (SASL, channel-max, idle timeout, authzid policy) which can be overridden
         * by subsequent builder calls.
         */
        public Builder mode(ContainerMode mode) {
            this.mode = Objects.requireNonNull(mode);
            this.requireSasl = mode.saslRequired;
            this.channelMax = mode.channelMax;
            this.idleTimeout = mode.idleTimeout;
            this.proto0Accepted = mode.proto0Accepted;
            this.authzidMustBeEmpty = mode.authzidMustBeEmpty;
            return this;
        }

        public Builder proto0Accepted(boolean proto0Accepted) { this.proto0Accepted = proto0Accepted; return this; }
        public Builder authzidMustBeEmpty(boolean authzidMustBeEmpty) { this.authzidMustBeEmpty = authzidMustBeEmpty; return this; }

        public ContainerConfig build() {
            return new ContainerConfig(this);
        }
    }
}
