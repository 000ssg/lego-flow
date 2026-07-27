package ssg.legoflow.messaging.amqp.container;

import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.sasl.SaslAuthenticator;

import java.util.UUID;

/**
 * Configuration for {@link AmqpContainer}.
 *
 * @param containerId   the container identifier
 * @param host          the bind address
 * @param port          the listen port (0 for ephemeral)
 * @param maxFrameSize  the maximum frame size
 * @param channelMax    the maximum channel number
 * @param idleTimeout   idle timeout in milliseconds (0 = disabled)
 * @param requireSasl   whether SASL authentication is required
 * @param authenticator the SASL authenticator (null for default)
 * @since 1.0.0
 */
public record ContainerConfig(
        String containerId,
        String host,
        int port,
        int maxFrameSize,
        int channelMax,
        long idleTimeout,
        boolean requireSasl,
        SaslAuthenticator authenticator
) {

    /**
     * Returns a default configuration suitable for testing.
     *
     * @return a default configuration
     */
    public static ContainerConfig defaults() {
        return new ContainerConfig(
                "lego-flow-amqp-" + UUID.randomUUID().toString().substring(0, 8),
                "localhost", 0,
                AmqpConstants.DEFAULT_MAX_FRAME_SIZE,
                AmqpConstants.DEFAULT_CHANNEL_MAX,
                0, false, null
        );
    }

    /**
     * Returns a configuration for testing with SASL authentication.
     *
     * @param authenticator the authenticator
     * @return a test configuration with SASL
     */
    public static ContainerConfig withSasl(SaslAuthenticator authenticator) {
        return new ContainerConfig(
                "lego-flow-amqp-" + UUID.randomUUID().toString().substring(0, 8),
                "localhost", 0,
                AmqpConstants.DEFAULT_MAX_FRAME_SIZE,
                AmqpConstants.DEFAULT_CHANNEL_MAX,
                0, true, authenticator
        );
    }
}
