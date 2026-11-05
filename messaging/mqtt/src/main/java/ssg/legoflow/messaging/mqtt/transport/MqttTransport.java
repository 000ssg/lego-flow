package ssg.legoflow.messaging.mqtt.transport;

import ssg.legoflow.service.channel.ByteTransport;
import ssg.legoflow.service.channel.DataChannel;

/**
 * Service Provider Interface for MQTT packet transport.
 *
 * @since 0.1.0
 */
public interface MqttTransport extends ByteTransport {

    /**
     * Returns the underlying {@link DataChannel} for this transport.
     *
     * @return the data channel
     */
    DataChannel getChannel();
}
