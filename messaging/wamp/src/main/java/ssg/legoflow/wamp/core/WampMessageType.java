package ssg.legoflow.wamp.core;

/**
 * WAMP protocol message type codes as defined in the WAMP specification.
 *
 * @since 1.0.0
 */
public enum WampMessageType {
    HELLO(1),
    WELCOME(2),
    ABORT(3),
    CHALLENGE(4),
    AUTHENTICATE(5),
    GOODBYE(6),
    ERROR(8),
    PUBLISH(16),
    PUBLISHED(17),
    SUBSCRIBE(32),
    SUBSCRIBED(33),
    UNSUBSCRIBE(34),
    UNSUBSCRIBED(35),
    EVENT(36),
    CALL(48),
    CANCEL(49),
    RESULT(50),
    REGISTER(64),
    REGISTERED(65),
    UNREGISTER(66),
    UNREGISTERED(67),
    INVOCATION(68),
    INTERRUPT(69),
    YIELD(70);

    private final int code;

    WampMessageType(int code) {
        this.code = code;
    }

    /**
     * Returns the integer code for this message type.
     *
     * @return the WAMP message type code
     */
    public int code() {
        return code;
    }

    /**
     * Resolves a {@code WampMessageType} from its integer code.
     *
     * @param code the WAMP message type code
     * @return the corresponding message type
     * @throws IllegalArgumentException if the code is unknown
     */
    public static WampMessageType fromCode(int code) {
        for (var type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Unknown WAMP message type code: " + code);
    }
}
