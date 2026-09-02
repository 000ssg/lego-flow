package ssg.legoflow.messaging.amqp.container;

import ssg.legoflow.messaging.amqp.common.AmqpConstants;

/**
 * Vendor simulation mode for {@link AmqpContainer}.
 *
 * <p>Each mode defines broker-specific defaults for SASL, addressing, channel limits,
 * and timing. STANDARD mode uses the least-restrictive interpretation of the spec,
 * accepting the widest range of client inputs.
 *
 * @since 0.2.0
 */
public enum ContainerMode {

    /**
     * Standard AMQP 1.0 — least restrictive defaults.
     * Accepts SASL-first and proto-0 clients, any authzid, large channel-max.
     */
    STANDARD(
            false,                                          // SASL required
            true,                                           // proto-0 accepted
            false,                                          // authzid must be empty
            AmqpConstants.DEFAULT_CHANNEL_MAX,             // channel-max
            0L,                                             // idle timeout (disabled)
            "PLAIN,ANONYMOUS,EXTERNAL"                      // SASL mechanisms
    ),

    /**
     * RabbitMQ AMQP 1.0 plugin simulation.
     * Requires SASL, rejects proto-0, requires empty authzid.
     */
    RABBITMQ(
            true,                                           // SASL required
            false,                                          // proto-0 rejected
            true,                                           // authzid must be empty
            AmqpConstants.DEFAULT_CHANNEL_MAX,             // channel-max
            60_000L,                                        // idle timeout (60s)
            "PLAIN,ANONYMOUS"                               // SASL mechanisms
    ),

    /**
     * Apache Artemis simulation.
     * Accepts proto-0, allows any authzid, supports GSSAPI.
     */
    ARTEMIS(
            false,                                          // SASL not required
            true,                                           // proto-0 accepted
            false,                                          // authzid can be any value
            AmqpConstants.DEFAULT_CHANNEL_MAX,             // channel-max
            0L,                                             // idle timeout (disabled)
            "PLAIN,ANONYMOUS,EXTERNAL,GSSAPI"              // SASL mechanisms
    ),

    /**
     * Qpid Dispatch Router simulation.
     * ANONYMOUS-only SASL, uses signed short for channel-max internally.
     */
    QPID_DISPATCH(
            false,                                          // SASL not required
            true,                                           // proto-0 accepted
            false,                                          // authzid can be any value
            32767,                                          // channel-max (signed short)
            8_000L,                                         // idle timeout (8s)
            "ANONYMOUS"                                     // SASL mechanisms
    ),

    /**
     * IBM MQ simulation.
     * May send server-first OPEN, requires queue manager addressing.
     */
    IBM_MQ(
            false,                                          // SASL not required
            true,                                           // proto-0 accepted
            false,                                          // authzid can be any value
            AmqpConstants.DEFAULT_CHANNEL_MAX,             // channel-max
            0L,                                             // idle timeout (disabled)
            "PLAIN,ANONYMOUS,EXTERNAL"                      // SASL mechanisms
    );

    /** Whether SASL authentication is required. */
    public final boolean saslRequired;

    /** Whether proto-0 header exchange is accepted (vs SASL-first only). */
    public final boolean proto0Accepted;

    /** Whether authzid in sasl-init must be empty. */
    public final boolean authzidMustBeEmpty;

    /** Default maximum channel number. */
    public final int channelMax;

    /** Default idle timeout in milliseconds (0 = disabled). */
    public final long idleTimeout;

    /** Default SASL mechanisms (comma-separated). */
    public final String saslMechanisms;

    ContainerMode(boolean saslRequired, boolean proto0Accepted, boolean authzidMustBeEmpty,
                  int channelMax, long idleTimeout, String saslMechanisms) {
        this.saslRequired = saslRequired;
        this.proto0Accepted = proto0Accepted;
        this.authzidMustBeEmpty = authzidMustBeEmpty;
        this.channelMax = channelMax;
        this.idleTimeout = idleTimeout;
        this.saslMechanisms = saslMechanisms;
    }
}
