package ssg.legoflow.http2.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the HTTP/2 protocol backed by a DataChannel.
 *
 * <p>Used by the service layer to bridge between the NIO selector thread and the protocol.
 *
 * @since 0.3.0
 */
public final class PipelineHttp2Transport extends PipelineByteTransport implements Http2Transport {

    public PipelineHttp2Transport(DataChannel channel) {
        super(channel);
    }
}
