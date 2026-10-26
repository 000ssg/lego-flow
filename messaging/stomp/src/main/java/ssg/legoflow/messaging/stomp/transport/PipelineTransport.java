package ssg.legoflow.messaging.stomp.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * {@link StompTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 */
public final class PipelineTransport extends PipelineByteTransport implements StompTransport {

    public PipelineTransport(DataChannel channel) {
        super(channel);
    }
}
