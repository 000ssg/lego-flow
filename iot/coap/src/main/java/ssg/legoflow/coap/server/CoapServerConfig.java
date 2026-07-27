package ssg.legoflow.coap.server;

/**
 * Configuration for a {@link CoapServer}.
 *
 * @param port                 the UDP port to bind (default 5683)
 * @param maxMessageSize       the maximum CoAP message size in bytes
 * @param deduplicationWindow  the deduplication window in milliseconds
 * @param ackTimeout           the initial ACK timeout in milliseconds (RFC 7252: 2000ms)
 * @param ackRandomFactor      the ACK random factor (RFC 7252: 1.5)
 * @param maxRetransmit        the maximum number of retransmissions (RFC 7252: 4)
 * @param maxResourceBodySize  the maximum resource body size for blockwise transfer
 * @since 1.0.0
 */
public record CoapServerConfig(
        int port,
        int maxMessageSize,
        long deduplicationWindow,
        long ackTimeout,
        double ackRandomFactor,
        int maxRetransmit,
        int maxResourceBodySize
) {

    /** Default CoAP port. */
    public static final int DEFAULT_PORT = 5683;

    /** Default CoAP DTLS port. */
    public static final int DEFAULT_DTLS_PORT = 5684;

    /**
     * Creates a configuration with default RFC 7252 values.
     *
     * @return the default configuration
     * @since 1.0.0
     */
    public static CoapServerConfig defaults() {
        return new CoapServerConfig(
                DEFAULT_PORT,
                1152,
                120_000,
                2000,
                1.5,
                4,
                1048576
        );
    }

    /**
     * Creates a configuration with the given port and other defaults.
     *
     * @param port the UDP port
     * @return the configuration
     * @since 1.0.0
     */
    public static CoapServerConfig withPort(int port) {
        return new CoapServerConfig(
                port,
                1152,
                120_000,
                2000,
                1.5,
                4,
                1048576
        );
    }
}
