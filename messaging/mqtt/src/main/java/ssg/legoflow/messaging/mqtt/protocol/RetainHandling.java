package ssg.legoflow.messaging.mqtt.protocol;

/**
 * MQTT 5.0 retain handling options for subscriptions.
 *
 * <p>Controls whether retained messages are sent when a subscription is established.
 *
 * @since 0.1.0
 */
public enum RetainHandling {

    /** Send retained messages at the time of the subscribe. */
    SEND_ON_SUBSCRIBE(0),

    /** Send retained messages only if the subscription does not already exist. */
    SEND_IF_NEW(1),

    /** Do not send retained messages at the time of the subscribe. */
    DO_NOT_SEND(2);

    private final int value;

    RetainHandling(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value of this retain handling option.
     *
     * @return 0, 1, or 2
     */
    public int value() {
        return value;
    }

    /**
     * Resolves a {@code RetainHandling} from its numeric value.
     *
     * @param value the retain handling value (0, 1, or 2)
     * @return the matching option
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static RetainHandling fromValue(int value) {
        return switch (value) {
            case 0 -> SEND_ON_SUBSCRIBE;
            case 1 -> SEND_IF_NEW;
            case 2 -> DO_NOT_SEND;
            default -> throw new IllegalArgumentException("Invalid RetainHandling value: " + value);
        };
    }
}
