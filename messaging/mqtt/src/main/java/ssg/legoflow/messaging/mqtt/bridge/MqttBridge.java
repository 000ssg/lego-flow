package ssg.legoflow.messaging.mqtt.bridge;

import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.protocol.PublishPacket;

/**
 * Bridge between two MQTT brokers.
 *
 * <p>A bridge forwards messages and subscriptions between a local and a remote
 * broker, enabling distributed MQTT deployments. The bridge maintains its own
 * subscriptions on both sides to ensure messages flow correctly.</p>
 *
 * <p>Unlike a regular client, a bridge:
 * <ul>
 *   <li>Forwards subscriptions from local to remote (and vice versa)</li>
 *   <li>Relays publish messages between brokers</li>
 *   <li>Respects the original publisher's identity when forwarding</li>
 * </ul>
 *
 * @since 0.2.0
 */
public interface MqttBridge extends AutoCloseable {

    /**
     * Starts the bridge, establishing connections to both brokers.
     *
     * @throws Exception if connection fails
     */
    void start() throws Exception;

    /**
     * Stops the bridge, closing connections to both brokers.
     */
    void stop();

    /**
     * Bridges a topic filter from local to remote. Messages published to topics
     * matching this filter on the local broker will be forwarded to the remote
     * broker, and vice versa.
     *
     * @param localFilter  the topic filter on the local broker
     * @param remoteFilter the topic filter on the remote broker
     * @param qos          the QoS level for bridged messages
     */
    void bridgeTopic(String localFilter, String remoteFilter, QoS qos);

    /**
     * Removes a bridged topic.
     *
     * @param localFilter the local topic filter
     */
    void unbridgeTopic(String localFilter);

    /**
     * Returns whether the bridge is running.
     */
    boolean isRunning();

    @Override
    void close();
}
