package ssg.legoflow.mqtt.protocol;

/**
 * MQTT control packet types as defined in the MQTT specification.
 *
 * <p>Each packet type has a numeric value (1-15) used in the fixed header.
 *
 * @since 1.0.0
 */
public enum MqttPacketType {

    /** Client request to connect to server. */
    CONNECT(1),
    /** Connect acknowledgement. */
    CONNACK(2),
    /** Publish message. */
    PUBLISH(3),
    /** Publish acknowledgement (QoS 1). */
    PUBACK(4),
    /** Publish received (QoS 2, step 1). */
    PUBREC(5),
    /** Publish release (QoS 2, step 2). */
    PUBREL(6),
    /** Publish complete (QoS 2, step 3). */
    PUBCOMP(7),
    /** Subscribe to topics. */
    SUBSCRIBE(8),
    /** Subscribe acknowledgement. */
    SUBACK(9),
    /** Unsubscribe from topics. */
    UNSUBSCRIBE(10),
    /** Unsubscribe acknowledgement. */
    UNSUBACK(11),
    /** Ping request. */
    PINGREQ(12),
    /** Ping response. */
    PINGRESP(13),
    /** Disconnect notification. */
    DISCONNECT(14),
    /** Authentication exchange (MQTT 5.0 only). */
    AUTH(15);

    private final int value;

    MqttPacketType(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value of this packet type.
     *
     * @return the packet type value (1-15)
     */
    public int value() {
        return value;
    }

    /**
     * Resolves a {@code MqttPacketType} from its numeric value.
     *
     * @param value the packet type value
     * @return the matching packet type
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static MqttPacketType fromValue(int value) {
        for (MqttPacketType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MQTT packet type: " + value);
    }
}
