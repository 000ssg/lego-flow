package ssg.legoflow.mqtt.protocol;

/**
 * MQTT v3.1.1 CONNACK return codes.
 *
 * <p>Indicates the result of a connection attempt.
 *
 * @since 1.0.0
 */
public enum ConnectReturnCode {

    /** Connection accepted. */
    ACCEPTED(0),

    /** Connection refused: unacceptable protocol version. */
    UNACCEPTABLE_PROTOCOL(1),

    /** Connection refused: identifier rejected. */
    IDENTIFIER_REJECTED(2),

    /** Connection refused: server unavailable. */
    SERVER_UNAVAILABLE(3),

    /** Connection refused: bad user name or password. */
    BAD_CREDENTIALS(4),

    /** Connection refused: not authorized. */
    NOT_AUTHORIZED(5);

    private final int value;

    ConnectReturnCode(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value of this return code.
     *
     * @return the return code value (0-5)
     */
    public int value() {
        return value;
    }

    /**
     * Resolves a {@code ConnectReturnCode} from its numeric value.
     *
     * @param value the return code value
     * @return the matching return code
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static ConnectReturnCode fromValue(int value) {
        for (ConnectReturnCode code : values()) {
            if (code.value == value) {
                return code;
            }
        }
        throw new IllegalArgumentException("Unknown CONNACK return code: " + value);
    }
}
