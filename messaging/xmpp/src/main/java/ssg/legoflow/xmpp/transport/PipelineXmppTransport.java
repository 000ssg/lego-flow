package ssg.legoflow.xmpp.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * {@link XmppTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 */
public final class PipelineXmppTransport extends PipelineByteTransport implements XmppTransport {

    public PipelineXmppTransport(DataChannel channel) {
        super(channel);
    }
}
