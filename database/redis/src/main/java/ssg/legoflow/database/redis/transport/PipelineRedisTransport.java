package ssg.legoflow.database.redis.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the Redis protocol.
 *
 * @since 0.3.0
 */
public final class PipelineRedisTransport extends PipelineByteTransport implements RedisTransport {

    public PipelineRedisTransport(DataChannel channel) {
        super(channel);
    }
}