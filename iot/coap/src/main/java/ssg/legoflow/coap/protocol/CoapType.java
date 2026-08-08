package ssg.legoflow.coap.protocol;

/**
 * CoAP message types as defined in RFC 7252, Section 3.
 *
 * <p>The type field is a 2-bit unsigned integer in the CoAP message header
 * indicating the message type: Confirmable, Non-confirmable, Acknowledgement, or Reset.
 *
 * @since 0.1.0
 */
public enum CoapType {

    /** Confirmable message requiring acknowledgement. */
    CONFIRMABLE(0),

    /** Non-confirmable message not requiring acknowledgement. */
    NON_CONFIRMABLE(1),

    /** Acknowledgement of a confirmable message. */
    ACKNOWLEDGEMENT(2),

    /** Reset message indicating inability to process. */
    RESET(3);

    private final int value;

    CoapType(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value used in the CoAP message header.
     *
     * @return the type value (0-3)
     * @since 0.1.0
     */
    public int value() {
        return value;
    }

    /**
     * Resolves a {@code CoapType} from the given numeric value.
     *
     * @param value the type value from the message header
     * @return the matching type
     * @throws IllegalArgumentException if the value is not recognized
     * @since 0.1.0
     */
    public static CoapType fromValue(int value) {
        return switch (value) {
            case 0 -> CONFIRMABLE;
            case 1 -> NON_CONFIRMABLE;
            case 2 -> ACKNOWLEDGEMENT;
            case 3 -> RESET;
            default -> throw new IllegalArgumentException("Unknown CoAP type: " + value);
        };
    }
}
