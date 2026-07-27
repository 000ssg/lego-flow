package ssg.legoflow.http3.quic;

/**
 * QUIC transport parameters as defined in RFC 9000 section 18.2.
 *
 * <p>These parameters are exchanged during the TLS handshake and
 * govern connection behaviour such as flow control limits, stream
 * limits, and idle timeout.</p>
 *
 * <p>Use the {@link Builder} to construct instances with custom values.
 * Default values follow the recommendations in RFC 9000.</p>
 *
 * @since 1.0.0
 */
public record QuicSettings(
        long maxIdleTimeout,
        int maxUdpPayloadSize,
        long initialMaxData,
        long initialMaxStreamDataBidiLocal,
        long initialMaxStreamDataBidiRemote,
        long initialMaxStreamDataUni,
        long initialMaxStreamsBidi,
        long initialMaxStreamsUni,
        int ackDelayExponent,
        int maxAckDelay,
        boolean disableActiveMigration,
        int activeConnectionIdLimit
) {

    /** Default maximum idle timeout in milliseconds. */
    public static final long DEFAULT_MAX_IDLE_TIMEOUT = 30_000;

    /** Default (initial) maximum UDP payload size. */
    public static final int DEFAULT_MAX_UDP_PAYLOAD_SIZE = 1200;

    /** Maximum UDP payload size allowed by the specification. */
    public static final int MAX_UDP_PAYLOAD_SIZE = 65527;

    /** Default initial maximum data on the connection. */
    public static final long DEFAULT_INITIAL_MAX_DATA = 1_048_576;

    /** Default initial maximum stream data for bidirectional streams (local). */
    public static final long DEFAULT_INITIAL_MAX_STREAM_DATA_BIDI_LOCAL = 262_144;

    /** Default initial maximum stream data for bidirectional streams (remote). */
    public static final long DEFAULT_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE = 262_144;

    /** Default initial maximum stream data for unidirectional streams. */
    public static final long DEFAULT_INITIAL_MAX_STREAM_DATA_UNI = 262_144;

    /** Default initial maximum number of bidirectional streams. */
    public static final long DEFAULT_INITIAL_MAX_STREAMS_BIDI = 100;

    /** Default initial maximum number of unidirectional streams. */
    public static final long DEFAULT_INITIAL_MAX_STREAMS_UNI = 100;

    /** Default ACK delay exponent. */
    public static final int DEFAULT_ACK_DELAY_EXPONENT = 3;

    /** Default maximum ACK delay in milliseconds. */
    public static final int DEFAULT_MAX_ACK_DELAY = 25;

    /** Default active connection ID limit. */
    public static final int DEFAULT_ACTIVE_CONNECTION_ID_LIMIT = 2;

    /**
     * Creates settings with all default values.
     *
     * @since 1.0.0
     */
    public QuicSettings() {
        this(
                DEFAULT_MAX_IDLE_TIMEOUT,
                DEFAULT_MAX_UDP_PAYLOAD_SIZE,
                DEFAULT_INITIAL_MAX_DATA,
                DEFAULT_INITIAL_MAX_STREAM_DATA_BIDI_LOCAL,
                DEFAULT_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE,
                DEFAULT_INITIAL_MAX_STREAM_DATA_UNI,
                DEFAULT_INITIAL_MAX_STREAMS_BIDI,
                DEFAULT_INITIAL_MAX_STREAMS_UNI,
                DEFAULT_ACK_DELAY_EXPONENT,
                DEFAULT_MAX_ACK_DELAY,
                false,
                DEFAULT_ACTIVE_CONNECTION_ID_LIMIT
        );
    }

    /**
     * Validates the transport parameters.
     *
     * @throws IllegalArgumentException if any parameter is out of range
     * @since 1.0.0
     */
    public QuicSettings {
        if (maxIdleTimeout < 0) {
            throw new IllegalArgumentException("maxIdleTimeout must be non-negative: " + maxIdleTimeout);
        }
        if (maxUdpPayloadSize < 1200 || maxUdpPayloadSize > MAX_UDP_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "maxUdpPayloadSize must be between 1200 and " + MAX_UDP_PAYLOAD_SIZE + ": " + maxUdpPayloadSize);
        }
        if (initialMaxData < 0) {
            throw new IllegalArgumentException("initialMaxData must be non-negative: " + initialMaxData);
        }
        if (initialMaxStreamDataBidiLocal < 0) {
            throw new IllegalArgumentException(
                    "initialMaxStreamDataBidiLocal must be non-negative: " + initialMaxStreamDataBidiLocal);
        }
        if (initialMaxStreamDataBidiRemote < 0) {
            throw new IllegalArgumentException(
                    "initialMaxStreamDataBidiRemote must be non-negative: " + initialMaxStreamDataBidiRemote);
        }
        if (initialMaxStreamDataUni < 0) {
            throw new IllegalArgumentException(
                    "initialMaxStreamDataUni must be non-negative: " + initialMaxStreamDataUni);
        }
        if (initialMaxStreamsBidi < 0) {
            throw new IllegalArgumentException(
                    "initialMaxStreamsBidi must be non-negative: " + initialMaxStreamsBidi);
        }
        if (initialMaxStreamsUni < 0) {
            throw new IllegalArgumentException(
                    "initialMaxStreamsUni must be non-negative: " + initialMaxStreamsUni);
        }
        if (ackDelayExponent < 0 || ackDelayExponent > 20) {
            throw new IllegalArgumentException("ackDelayExponent must be 0-20: " + ackDelayExponent);
        }
        if (maxAckDelay < 0 || maxAckDelay > 16383) {
            throw new IllegalArgumentException("maxAckDelay must be 0-16383: " + maxAckDelay);
        }
        if (activeConnectionIdLimit < 2) {
            throw new IllegalArgumentException(
                    "activeConnectionIdLimit must be at least 2: " + activeConnectionIdLimit);
        }
    }

    /**
     * Returns a new {@link Builder} initialised with all default values.
     *
     * @return a new builder
     * @since 1.0.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link QuicSettings} with fluent setter methods.
     *
     * @since 1.0.0
     */
    public static class Builder {

        private long maxIdleTimeout = DEFAULT_MAX_IDLE_TIMEOUT;
        private int maxUdpPayloadSize = DEFAULT_MAX_UDP_PAYLOAD_SIZE;
        private long initialMaxData = DEFAULT_INITIAL_MAX_DATA;
        private long initialMaxStreamDataBidiLocal = DEFAULT_INITIAL_MAX_STREAM_DATA_BIDI_LOCAL;
        private long initialMaxStreamDataBidiRemote = DEFAULT_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE;
        private long initialMaxStreamDataUni = DEFAULT_INITIAL_MAX_STREAM_DATA_UNI;
        private long initialMaxStreamsBidi = DEFAULT_INITIAL_MAX_STREAMS_BIDI;
        private long initialMaxStreamsUni = DEFAULT_INITIAL_MAX_STREAMS_UNI;
        private int ackDelayExponent = DEFAULT_ACK_DELAY_EXPONENT;
        private int maxAckDelay = DEFAULT_MAX_ACK_DELAY;
        private boolean disableActiveMigration = false;
        private int activeConnectionIdLimit = DEFAULT_ACTIVE_CONNECTION_ID_LIMIT;

        /**
         * Sets the maximum idle timeout in milliseconds.
         *
         * @param maxIdleTimeout the timeout value
         * @return this builder
         * @since 1.0.0
         */
        public Builder maxIdleTimeout(long maxIdleTimeout) {
            this.maxIdleTimeout = maxIdleTimeout;
            return this;
        }

        /**
         * Sets the maximum UDP payload size.
         *
         * @param maxUdpPayloadSize the payload size (1200 to 65527)
         * @return this builder
         * @since 1.0.0
         */
        public Builder maxUdpPayloadSize(int maxUdpPayloadSize) {
            this.maxUdpPayloadSize = maxUdpPayloadSize;
            return this;
        }

        /**
         * Sets the initial maximum data on the connection.
         *
         * @param initialMaxData the data limit in bytes
         * @return this builder
         * @since 1.0.0
         */
        public Builder initialMaxData(long initialMaxData) {
            this.initialMaxData = initialMaxData;
            return this;
        }

        /**
         * Sets the initial max stream data for locally-initiated bidirectional streams.
         *
         * @param initialMaxStreamDataBidiLocal the data limit in bytes
         * @return this builder
         * @since 1.0.0
         */
        public Builder initialMaxStreamDataBidiLocal(long initialMaxStreamDataBidiLocal) {
            this.initialMaxStreamDataBidiLocal = initialMaxStreamDataBidiLocal;
            return this;
        }

        /**
         * Sets the initial max stream data for remotely-initiated bidirectional streams.
         *
         * @param initialMaxStreamDataBidiRemote the data limit in bytes
         * @return this builder
         * @since 1.0.0
         */
        public Builder initialMaxStreamDataBidiRemote(long initialMaxStreamDataBidiRemote) {
            this.initialMaxStreamDataBidiRemote = initialMaxStreamDataBidiRemote;
            return this;
        }

        /**
         * Sets the initial max stream data for unidirectional streams.
         *
         * @param initialMaxStreamDataUni the data limit in bytes
         * @return this builder
         * @since 1.0.0
         */
        public Builder initialMaxStreamDataUni(long initialMaxStreamDataUni) {
            this.initialMaxStreamDataUni = initialMaxStreamDataUni;
            return this;
        }

        /**
         * Sets the initial maximum number of bidirectional streams.
         *
         * @param initialMaxStreamsBidi the stream limit
         * @return this builder
         * @since 1.0.0
         */
        public Builder initialMaxStreamsBidi(long initialMaxStreamsBidi) {
            this.initialMaxStreamsBidi = initialMaxStreamsBidi;
            return this;
        }

        /**
         * Sets the initial maximum number of unidirectional streams.
         *
         * @param initialMaxStreamsUni the stream limit
         * @return this builder
         * @since 1.0.0
         */
        public Builder initialMaxStreamsUni(long initialMaxStreamsUni) {
            this.initialMaxStreamsUni = initialMaxStreamsUni;
            return this;
        }

        /**
         * Sets the ACK delay exponent.
         *
         * @param ackDelayExponent the exponent (0-20)
         * @return this builder
         * @since 1.0.0
         */
        public Builder ackDelayExponent(int ackDelayExponent) {
            this.ackDelayExponent = ackDelayExponent;
            return this;
        }

        /**
         * Sets the maximum ACK delay in milliseconds.
         *
         * @param maxAckDelay the delay (0-16383)
         * @return this builder
         * @since 1.0.0
         */
        public Builder maxAckDelay(int maxAckDelay) {
            this.maxAckDelay = maxAckDelay;
            return this;
        }

        /**
         * Disables or enables active migration.
         *
         * @param disableActiveMigration {@code true} to disable migration
         * @return this builder
         * @since 1.0.0
         */
        public Builder disableActiveMigration(boolean disableActiveMigration) {
            this.disableActiveMigration = disableActiveMigration;
            return this;
        }

        /**
         * Sets the active connection ID limit.
         *
         * @param activeConnectionIdLimit the limit (minimum 2)
         * @return this builder
         * @since 1.0.0
         */
        public Builder activeConnectionIdLimit(int activeConnectionIdLimit) {
            this.activeConnectionIdLimit = activeConnectionIdLimit;
            return this;
        }

        /**
         * Builds the {@link QuicSettings} instance.
         *
         * @return a new validated {@code QuicSettings}
         * @throws IllegalArgumentException if any parameter is out of range
         * @since 1.0.0
         */
        public QuicSettings build() {
            return new QuicSettings(
                    maxIdleTimeout, maxUdpPayloadSize, initialMaxData,
                    initialMaxStreamDataBidiLocal, initialMaxStreamDataBidiRemote,
                    initialMaxStreamDataUni, initialMaxStreamsBidi, initialMaxStreamsUni,
                    ackDelayExponent, maxAckDelay, disableActiveMigration,
                    activeConnectionIdLimit
            );
        }
    }
}
