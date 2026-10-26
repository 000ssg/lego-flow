package ssg.legoflow.messaging.nats.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * {@link NatsTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 */
public final class PipelineNatsTransport extends PipelineByteTransport implements NatsTransport {

    public PipelineNatsTransport(DataChannel channel) {
        super(channel);
    }
}
