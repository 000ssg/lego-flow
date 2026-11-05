package ssg.legoflow.messaging.stomp.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;
import ssg.legoflow.service.channel.ByteTransport;

/**
 * {@link ByteTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 */
public final class PipelineTransport extends PipelineByteTransport implements ByteTransport {

    public PipelineTransport(DataChannel channel) {
        super(channel);
    }
}
