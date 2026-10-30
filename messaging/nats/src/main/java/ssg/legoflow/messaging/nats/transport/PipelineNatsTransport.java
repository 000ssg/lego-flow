package ssg.legoflow.messaging.nats.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;
import ssg.legoflow.service.channel.ByteTransport;

/**
 * {@link ByteTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 */
public final class PipelineNatsTransport extends PipelineByteTransport implements ByteTransport {

    public PipelineNatsTransport(DataChannel channel) {
        super(channel);
    }
}
