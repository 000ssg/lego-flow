package ssg.legoflow.http.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the HTTP protocol backed by a DataChannel.
 *
 * <p>Used by the service layer to bridge between the NIO selector thread and the protocol.
 *
 * @since 0.3.0
 */
public final class PipelineHttpTransport extends PipelineByteTransport implements HttpTransport {

    public PipelineHttpTransport(DataChannel channel) {
        super(channel);
    }
}
