package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * {@link AmqpTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 *
 * @since 0.1.0
 */
public final class PipelineTransport extends PipelineByteTransport implements AmqpTransport {

    public PipelineTransport(DataChannel channel) {
        super(channel);
    }
}
