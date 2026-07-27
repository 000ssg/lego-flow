package ssg.legoflow.messaging.nats.jetstream;

/**
 * JetStream consumer acknowledgement policy.
 *
 * @since 1.0.0
 */
public enum AckPolicy {

    /** No acknowledgement required — messages delivered and forgotten. */
    NONE("none"),

    /** All messages up to and including the acked sequence are acknowledged. */
    ALL("all"),

    /** Each message must be individually acknowledged. */
    EXPLICIT("explicit");

    private final String value;

    AckPolicy(String value) {
        this.value = value;
    }

    /**
     * Returns the wire-format value.
     *
     * @return the string value
     */
    public String value() {
        return value;
    }

    /**
     * Parses the policy from a string value.
     *
     * @param value the string value
     * @return the policy
     * @throws IllegalArgumentException if unknown
     */
    public static AckPolicy fromValue(String value) {
        for (AckPolicy p : values()) {
            if (p.value.equals(value)) return p;
        }
        throw new IllegalArgumentException("Unknown ack policy: " + value);
    }
}
