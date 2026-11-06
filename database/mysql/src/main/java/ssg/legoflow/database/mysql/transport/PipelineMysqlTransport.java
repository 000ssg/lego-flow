package ssg.legoflow.database.mysql.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the MySQL protocol backed by a DataChannel.
 *
 * <p>Used by the service layer to bridge between the NIO selector thread and the protocol.
 *
 * @since 0.3.0
 */
public final class PipelineMysqlTransport extends PipelineByteTransport implements MysqlTransport {

    public PipelineMysqlTransport(DataChannel channel) {
        super(channel);
    }
}
