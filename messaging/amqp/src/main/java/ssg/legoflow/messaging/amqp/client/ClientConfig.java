package ssg.legoflow.messaging.amqp.client;

import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.sasl.SaslMechanism;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuration for {@link AmqpClient} with a fluent builder API.
 *
 * <p>Supports broker-aware configuration via {@link BrokerMode}, which
 * pre-fills settle modes, address formatting, and protocol preferences
 * for interop with specific AMQP 1.0 brokers.
 *
 * @since 0.1.0
 */
public final class ClientConfig {

    private final String host;
    private final int port;
    private final String containerId;
    private final int maxFrameSize;
    private final int channelMax;
    private final long idleTimeout;
    private final Duration connectTimeout;
    private final SaslMechanism saslMechanism;
    private final String username;
    private final String password;
    private final BrokerMode brokerMode;
    private final int sndSettleMode;
    private final int rcvSettleMode;

    private ClientConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.containerId = builder.containerId;
        this.maxFrameSize = builder.maxFrameSize;
        this.channelMax = builder.channelMax;
        this.idleTimeout = builder.idleTimeout;
        this.connectTimeout = builder.connectTimeout;
        this.saslMechanism = builder.saslMechanism;
        this.username = builder.username;
        this.password = builder.password;
        this.brokerMode = builder.brokerMode;
        this.sndSettleMode = builder.sndSettleMode;
        this.rcvSettleMode = builder.rcvSettleMode;
    }

    /** Returns the container hostname. */
    public String host() { return host; }

    /** Returns the container port. */
    public int port() { return port; }

    /** Returns the client container identifier. */
    public String containerId() { return containerId; }

    /** Returns the maximum frame size. */
    public int maxFrameSize() { return maxFrameSize; }

    /** Returns the maximum channel number. */
    public int channelMax() { return channelMax; }

    /** Returns the idle timeout in milliseconds (0 = disabled). */
    public long idleTimeout() { return idleTimeout; }

    /** Returns the broker target mode. */
    public BrokerMode brokerMode() { return brokerMode; }

    /** Returns the sender settle mode (0=unsettled, 1=settled, 2=mixed). */
    public int sndSettleMode() { return sndSettleMode; }

    /** Returns the receiver settle mode (0=first, 1=second). */
    public int rcvSettleMode() { return rcvSettleMode; }

    /** Returns the connect timeout. */
    public Duration connectTimeout() { return connectTimeout; }

    /** Returns the SASL mechanism, or null if SASL is not used. */
    public SaslMechanism saslMechanism() { return saslMechanism; }

    /** Returns the username for SASL PLAIN authentication, or null. */
    public String username() { return username; }

    /** Returns the password for SASL PLAIN authentication, or null. */
    public String password() { return password; }

    /**
     * Creates a builder with default settings.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a config for connecting to localhost on the given port.
     *
     * @param port the port
     * @return a configured instance
     */
    public static ClientConfig localhost(int port) {
        return new Builder().port(port).build();
    }

    /**
     * Fluent builder for {@link ClientConfig}.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private String host = "localhost";
        private int port = AmqpConstants.DEFAULT_PORT;
        private String containerId = "lego-flow-client-" + UUID.randomUUID().toString().substring(0, 8);
        private int maxFrameSize = AmqpConstants.DEFAULT_MAX_FRAME_SIZE;
        private int channelMax = AmqpConstants.DEFAULT_CHANNEL_MAX;
        private long idleTimeout = 0;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private SaslMechanism saslMechanism;
        private String username;
        private String password;
        private BrokerMode brokerMode = BrokerMode.STANDARD;
        private int sndSettleMode = 0; // unsettled
        private int rcvSettleMode = 0; // first

        /** Sets the container hostname. */
        public Builder host(String host) { this.host = Objects.requireNonNull(host); return this; }

        /** Sets the container port. */
        public Builder port(int port) { this.port = port; return this; }

        /** Sets the client container identifier. */
        public Builder containerId(String containerId) { this.containerId = Objects.requireNonNull(containerId); return this; }

        /** Sets the maximum frame size. */
        public Builder maxFrameSize(int maxFrameSize) { this.maxFrameSize = maxFrameSize; return this; }

        /** Sets the maximum channel number. */
        public Builder channelMax(int channelMax) { this.channelMax = channelMax; return this; }

        /** Sets the idle timeout in milliseconds. */
        public Builder idleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; return this; }

        /** Sets the connect timeout. */
        public Builder connectTimeout(Duration connectTimeout) { this.connectTimeout = Objects.requireNonNull(connectTimeout); return this; }

        /** Sets the SASL mechanism for authentication. */
        public Builder saslMechanism(SaslMechanism saslMechanism) { this.saslMechanism = saslMechanism; return this; }

        /** Sets the username for SASL PLAIN authentication. */
        public Builder username(String username) { this.username = username; return this; }

        /** Sets the password for SASL PLAIN authentication. */
        public Builder password(String password) { this.password = password; return this; }

        /**
         * Sets the broker target mode. This pre-fills mode-specific defaults
         * (settle modes, address format) which can be overridden by subsequent
         * builder calls.
         *
         * @param mode the broker mode
         * @return this builder
         */
        public Builder brokerMode(BrokerMode mode) {
            this.brokerMode = Objects.requireNonNull(mode);
            this.sndSettleMode = mode.sndSettleMode();
            this.rcvSettleMode = mode.rcvSettleMode();
            return this;
        }

        /** Sets the sender settle mode (0=unsettled, 1=settled, 2=mixed). */
        public Builder sndSettleMode(int sndSettleMode) { this.sndSettleMode = sndSettleMode; return this; }

        /** Sets the receiver settle mode (0=first, 1=second). */
        public Builder rcvSettleMode(int rcvSettleMode) { this.rcvSettleMode = rcvSettleMode; return this; }

        /** Builds the configuration. */
        public ClientConfig build() { return new ClientConfig(this); }
    }
}
