package ssg.legoflow.messaging.mqtt.client;

import ssg.legoflow.messaging.mqtt.protocol.PublishPacket;

/**
 * Callback interface for MQTT client lifecycle events and message delivery.
 *
 * @since 0.1.0
 */
public interface MqttCallback {

    /**
     * Called when a message is received on a subscribed topic.
     *
     * @param topic   the topic the message was published to
     * @param message the full PUBLISH packet
     */
    void onMessage(String topic, PublishPacket message);

    /**
     * Called when the connection to the broker is lost unexpectedly.
     *
     * @param cause the cause of the connection loss
     */
    void onConnectionLost(Throwable cause);

    /**
     * Called when the client has successfully reconnected after a connection loss.
     */
    void onReconnected();

    /**
     * Called when a QoS 1 or 2 message delivery is complete.
     *
     * @param packetId the packet identifier of the delivered message
     */
    void onDeliveryComplete(int packetId);
}
