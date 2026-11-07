package ssg.legoflow.database.postgresql.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the PostgreSQL protocol.
 *
 * @since 0.3.0
 */
public final class PipelinePgTransport extends PipelineByteTransport implements PgTransport {

    public PipelinePgTransport(DataChannel channel) {
        super(channel);
    }
}
