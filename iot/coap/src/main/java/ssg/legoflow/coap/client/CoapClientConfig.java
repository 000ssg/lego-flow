package ssg.legoflow.coap.client;

import java.util.Objects;

/**
 * Configuration for a {@link CoapClient}.
 *
 * @param host               the target host
 * @param port               the target port
 * @param ackTimeout         the ACK timeout in milliseconds (RFC 7252: 2000ms)
 * @param maxRetransmit      the maximum number of retransmissions (RFC 7252: 4)
 * @param preferredBlockSize the preferred block size for blockwise transfer
 * @since 1.0.0
 */
public record CoapClientConfig(
        String host,
        int port,
        long ackTimeout,
        int maxRetransmit,
        int preferredBlockSize
) {

    /**
     * Compact constructor with validation.
     *
     * @param host               the target host; must not be {@code null}
     * @param port               the target port; must be positive
     * @param ackTimeout         the ACK timeout in milliseconds
     * @param maxRetransmit      the maximum retransmissions
     * @param preferredBlockSize the preferred block size
     */
    public CoapClientConfig {
        Objects.requireNonNull(host, "host must not be null");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
    }

    /**
     * Creates a default configuration for the given host.
     *
     * @param host the target host
     * @return the default configuration
     * @since 1.0.0
     */
    public static CoapClientConfig defaults(String host) {
        return new CoapClientConfig(host, 5683, 2000, 4, 512);
    }

    /**
     * Creates a default configuration for the given host and port.
     *
     * @param host the target host
     * @param port the target port
     * @return the default configuration
     * @since 1.0.0
     */
    public static CoapClientConfig defaults(String host, int port) {
        return new CoapClientConfig(host, port, 2000, 4, 512);
    }
}
