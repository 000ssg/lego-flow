package ssg.legoflow.xmpp.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;
import ssg.legoflow.service.channel.ByteTransport;

/**
 * {@link ByteTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 */
public final class PipelineXmppTransport extends PipelineByteTransport implements ByteTransport {

    public PipelineXmppTransport(DataChannel channel) {
        super(channel);
    }
}
