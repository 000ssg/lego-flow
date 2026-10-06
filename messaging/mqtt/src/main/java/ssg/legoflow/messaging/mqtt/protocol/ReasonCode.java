package ssg.legoflow.messaging.mqtt.protocol;

/**
 * MQTT 5.0 reason codes used across various packet types.
 *
 * <p>Reason codes provide more detailed feedback than the v3.1.1 return codes.
 *
 * @since 0.1.0
 */
public enum ReasonCode {

    /** Operation completed successfully. */
    SUCCESS(0x00),

    /** Normal disconnection (same byte as SUCCESS). */
    NORMAL_DISCONNECTION(0x00),

    /** Granted QoS 0. */
    GRANTED_QOS_0(0x00),

    /** Granted QoS 1. */
    GRANTED_QOS_1(0x01),

    /** Granted QoS 2. */
    GRANTED_QOS_2(0x02),

    /** Disconnect with will message. */
    DISCONNECT_WITH_WILL(0x04),

    /** No matching subscribers for the published topic. */
    NO_MATCHING_SUBSCRIBERS(0x10),

    /** Unspecified error. */
    UNSPECIFIED_ERROR(0x80),

    /** Malformed packet received. */
    MALFORMED_PACKET(0x81),

    /** Protocol error detected. */
    PROTOCOL_ERROR(0x82),

    /** Implementation-specific error. */
    IMPLEMENTATION_SPECIFIC(0x83),

    /** Client is not authorized. */
    NOT_AUTHORIZED(0x87),

    /** Server is busy. */
    SERVER_BUSY(0x89),

    /** Server is shutting down. */
    SERVER_SHUTTING_DOWN(0x8B),

    /** Keep alive timeout exceeded. */
    KEEP_ALIVE_TIMEOUT(0x8D),

    /** Session taken over by another client. */
    SESSION_TAKEN_OVER(0x8E),

    /** Topic filter is invalid. */
    TOPIC_FILTER_INVALID(0x8F),

    /** Topic name is invalid. */
    TOPIC_NAME_INVALID(0x90),

    /** Packet identifier is already in use. */
    PACKET_ID_IN_USE(0x91),

    /** Packet identifier not found. */
    PACKET_ID_NOT_FOUND(0x92),

    /** Quota exceeded. */
    QUOTA_EXCEEDED(0x97),

    /** Payload format is invalid. */
    PAYLOAD_FORMAT_INVALID(0x99);

    private final int value;

    ReasonCode(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value of this reason code.
     *
     * @return the reason code byte value
     */
    public int value() {
        return value;
    }

    /**
     * Returns {@code true} if this reason code indicates an error (value >= 0x80).
     *
     * @return whether this is an error reason code
     */
    public boolean isError() {
        return value >= 0x80;
    }

    /**
     * Resolves a {@code ReasonCode} from its numeric value.
     *
     * <p>Note: multiple reason codes share the same byte value (e.g. SUCCESS,
     * NORMAL_DISCONNECTION, and GRANTED_QOS_0 are all 0x00). This method
     * returns the first match.
     *
     * @param value the reason code byte value
     * @return the matching reason code
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static ReasonCode fromValue(int value) {
        for (ReasonCode code : values()) {
            if (code.value == value) {
                return code;
            }
        }
        throw new IllegalArgumentException("Unknown reason code: 0x" + Integer.toHexString(value));
    }
}
