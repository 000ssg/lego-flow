package ssg.legoflow.mqtt.protocol;

/**
 * Represents an MQTT topic subscription with QoS and v5.0 subscription options.
 *
 * @param topicFilter       the topic filter string (may contain wildcards)
 * @param qos               the maximum QoS level for this subscription
 * @param noLocal           if {@code true}, do not forward messages published by this client (MQTT 5.0)
 * @param retainAsPublished if {@code true}, retain the RETAIN flag as published (MQTT 5.0)
 * @param retainHandling    controls how retained messages are delivered on subscribe (MQTT 5.0)
 * @since 1.0.0
 */
public record TopicSubscription(
        String topicFilter,
        QoS qos,
        boolean noLocal,
        boolean retainAsPublished,
        RetainHandling retainHandling
) {

    /**
     * Creates a simple subscription with default v5.0 options.
     *
     * @param topicFilter the topic filter
     * @param qos         the QoS level
     */
    public TopicSubscription(String topicFilter, QoS qos) {
        this(topicFilter, qos, false, false, RetainHandling.SEND_ON_SUBSCRIBE);
    }
}
