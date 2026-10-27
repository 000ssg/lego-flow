package ssg.legoflow.messaging.kafka.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;
import ssg.legoflow.service.channel.ByteTransport;

/**
 * {@link ByteTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 */
public final class PipelineKafkaTransport extends PipelineByteTransport implements ByteTransport {

    public PipelineKafkaTransport(DataChannel channel) {
        super(channel);
    }
}
