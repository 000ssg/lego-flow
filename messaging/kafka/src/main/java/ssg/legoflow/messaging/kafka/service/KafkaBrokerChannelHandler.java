package ssg.legoflow.messaging.kafka.service;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.transport.PipelineKafkaTransport;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Channel handler for the Kafka broker service. Bridges pipeline events to per-connection
 * transports and hands them to the headless {@link KafkaBroker} core.
 *
 * <p>Pattern mirrors the NATS/STOMP server channel handler: one transport per
 * {@link DataChannel}, mapped via a {@link ConcurrentHashMap}. The transport is created on
 * connect and handed to the core; read/write bytes flow through it; disconnect tears it down.
 */
public final class KafkaBrokerChannelHandler implements ChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBrokerChannelHandler.class);

    private final KafkaBrokerService service;
    private final ConcurrentHashMap<DataChannel, PipelineKafkaTransport> transportByChannel = new ConcurrentHashMap<>();

    public KafkaBrokerChannelHandler(KafkaBrokerService service) {
        this.service = service;
    }

    @Override
    public void onConnect(DataChannel channel) {
        LOG.debug("Client channel accepted");
        if (channel instanceof TcpDataChannel) {
            var transport = new PipelineKafkaTransport(channel);
            transportByChannel.put(channel, transport);
            try {
                var broker = service.getBroker();
                if (broker != null) {
                    broker.handleConnection(transport);
                } else {
                    LOG.error("Kafka broker core not initialized for service: {}", service.getDescriptor().name());
                    transportByChannel.remove(channel);
                    transport.close();
                    try { channel.close(); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                LOG.error("Failed to handle connection: {}", e.getMessage(), e);
                transportByChannel.remove(channel);
                transport.close();
                try { channel.close(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        var transport = transportByChannel.get(channel);
        if (transport != null) {
            transport.onRead(channel, data);
        }
    }

    @Override
    public void onWrite(DataChannel channel) {
        var transport = transportByChannel.get(channel);
        if (transport != null) {
            transport.onWrite(channel);
        }
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        var transport = transportByChannel.remove(channel);
        if (transport != null) {
            transport.close();
        }
        LOG.debug("Client channel disconnected");
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.warn("Client channel error", cause);
        onDisconnect(channel);
    }
}
