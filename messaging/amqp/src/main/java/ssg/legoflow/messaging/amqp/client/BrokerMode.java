package ssg.legoflow.messaging.amqp.client;

/**
 * Broker target mode for {@link AmqpClient}.
 *
 * <p>Each mode defines client-side adaptations needed to interoperate
 * with a specific broker. STANDARD uses the spec defaults and is the
 * most portable choice.
 *
 * <p>When connecting, the client sends a SASL-first (proto-3) header
 * by default. If the server responds with AMQP_HEADER (proto-0), the
 * client falls back to proto-0 exchange. This works with all brokers.
 *
 * @since 0.2.0
 */
public enum BrokerMode {

    /** Standard AMQP 1.0 — least restrictive defaults. */
    STANDARD,

    /** RabbitMQ AMQP 1.0 plugin. Requires SASL, strips /queues/ prefix. */
    RABBITMQ,

    /** Apache Artemis. Accepts proto-0, supports GSSAPI. */
    ARTEMIS,

    /** Qpid Dispatch Router. ANONYMOUS SASL, uses closest: address format. */
    QPID_DISPATCH,

    /** IBM MQ. May send server-first OPEN. */
    IBM_MQ;

    /**
     * Returns whether the client should send SASL_HEADER first (proto-3).
     * All modes default to true for maximum compatibility.
     */
    public boolean saslFirst() {
        return true;
    }

    /**
     * Returns whether to prefix addresses with a broker-specific scheme.
     * QPID_DISPATCH uses "closest:" prefix; RABBITMQ uses "/queues/" prefix.
     */
    public String addressPrefix() {
        return switch (this) {
            case RABBITMQ -> "/queues/";
            case QPID_DISPATCH -> "closest:";
            default -> "";
        };
    }

    /**
     * Returns the sender settle mode to request (0=unsettled, 1=settled, 2=mixed).
     * STANDARD and ARTEMIS use unsettled(0). RABBITMQ uses mixed(2) for reliability.
     */
    public int sndSettleMode() {
        return 0; // unsettled — broker decides
    }

    /**
     * Returns the receiver settle mode to request (0=first, 1=second).
     * STANDARD uses first(0). RABBITMQ uses second(1) for at-least-once.
     */
    public int rcvSettleMode() {
        return switch (this) {
            case RABBITMQ -> 1;
            default -> 0;
        };
    }

    /**
     * Converts an application address to broker format.
     */
    public String formatAddress(String address) {
        if (address == null || address.isEmpty()) return address;
        String prefix = addressPrefix();
        if (prefix.isEmpty()) return address;
        // Don't double-prefix
        if (address.startsWith(prefix)) return address;
        return prefix + address;
    }
}
