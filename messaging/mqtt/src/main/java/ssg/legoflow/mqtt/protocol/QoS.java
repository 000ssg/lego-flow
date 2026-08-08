package ssg.legoflow.mqtt.protocol;

/**
 * MQTT Quality of Service levels.
 *
 * <p>Defines the three QoS levels for message delivery guarantees:
 * <ul>
 *   <li>{@link #AT_MOST_ONCE} — fire and forget (QoS 0)</li>
 *   <li>{@link #AT_LEAST_ONCE} — acknowledged delivery (QoS 1)</li>
 *   <li>{@link #EXACTLY_ONCE} — assured delivery (QoS 2)</li>
 * </ul>
 *
 * @since 0.1.0
 */
public enum QoS {

    /** QoS 0: at most once delivery. No acknowledgement. */
    AT_MOST_ONCE(0),

    /** QoS 1: at least once delivery. PUBACK acknowledgement. */
    AT_LEAST_ONCE(1),

    /** QoS 2: exactly once delivery. Four-part handshake. */
    EXACTLY_ONCE(2);

    private final int value;

    QoS(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric QoS value.
     *
     * @return 0, 1, or 2
     */
    public int value() {
        return value;
    }

    /**
     * Resolves a {@code QoS} from its numeric value.
     *
     * @param value the QoS value (0, 1, or 2)
     * @return the matching QoS level
     * @throws IllegalArgumentException if the value is not 0, 1, or 2
     */
    public static QoS fromValue(int value) {
        return switch (value) {
            case 0 -> AT_MOST_ONCE;
            case 1 -> AT_LEAST_ONCE;
            case 2 -> EXACTLY_ONCE;
            default -> throw new IllegalArgumentException("Invalid QoS value: " + value);
        };
    }
}
