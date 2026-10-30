package ssg.legoflow.messaging.kafka.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;
import ssg.legoflow.service.channel.ByteTransport;

/**
 * {@link ByteTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 *
 * <p>Uses the generic enqueue-only {@link PipelineByteTransport#send} — never writes
 * directly to the channel from the calling thread. This avoids the concurrent-write race
 * that caused message loss in NATS/STOMP/MQTT.
 */
public final class PipelineKafkaTransport extends PipelineByteTransport implements ByteTransport {

    public PipelineKafkaTransport(DataChannel channel) {
        super(channel);
    }
}
