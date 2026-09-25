package ssg.legoflow.messaging.stomp.server.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import ssg.legoflow.messaging.stomp.transport.PipelineTransport;
import ssg.legoflow.messaging.stomp.core.StompBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Channel handler for STOMP server service. Bridges pipeline events
 * to per-connection transports and hands them to the broker.
 *
 * <p>Pattern mirrors {@code AmqpContainerChannelHandler}: one transport
 * per DataChannel, mapped via {@link ConcurrentHashMap}.
 */
public final class StompServerChannelHandler implements ChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StompServerChannelHandler.class);

    private final StompServerService service;
    private final ConcurrentHashMap<DataChannel, PipelineTransport> transportByChannel = new ConcurrentHashMap<>();

    public StompServerChannelHandler(StompServerService service) {
        this.service = service;
    }

    @Override
    public void onConnect(DataChannel channel) {
        LOG.debug("Client channel accepted by broker");
        if (channel instanceof TcpDataChannel) {
            var transport = new PipelineTransport(channel);
            transportByChannel.put(channel, transport);
            try {
                if (service.getBroker() != null) {
                    service.getBroker().accept(transport);
                } else {
                    LOG.error("Broker not initialized for service: {}", service.getDescriptor().name());
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
        LOG.debug("Client channel disconnected from broker");
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.warn("Client channel error in broker", cause);
        onDisconnect(channel);
    }
}
