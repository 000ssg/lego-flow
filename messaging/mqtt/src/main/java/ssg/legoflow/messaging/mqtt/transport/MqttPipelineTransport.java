package ssg.legoflow.messaging.mqtt.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.ByteTransport;
import ssg.legoflow.service.channel.PipelineByteTransport;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * {@link MqttTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 */
public final class MqttPipelineTransport extends PipelineByteTransport implements MqttTransport {

    public MqttPipelineTransport(DataChannel channel) {
        super(channel);
    }

    @Override
    public DataChannel getChannel() {
        return super.getChannel();
    }

    /**
     * Restore original simple receive behavior: return immediately if data available,
     * otherwise wait once for the semaphore. Does NOT loop to fill the buffer.
     */
    @Override
    public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
        if (!isOpen()) return -1;
        if (peek() > 0) {
            return fetch(buffer);
        }
        try {
            if (!available.tryAcquire(timeout, unit)) return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
        return fetch(buffer);
    }
}
