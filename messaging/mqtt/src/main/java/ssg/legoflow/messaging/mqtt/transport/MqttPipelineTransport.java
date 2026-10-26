package ssg.legoflow.messaging.mqtt.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * {@link MqttTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 */
public final class MqttPipelineTransport extends PipelineByteTransport {

    public MqttPipelineTransport(DataChannel channel) {
        super(channel);
    }
}
