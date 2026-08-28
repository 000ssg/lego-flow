package ssg.legoflow.messaging.amqp.server.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import ssg.legoflow.messaging.amqp.transport.PipelineTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

public final class AmqpContainerChannelHandler implements ChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpContainerChannelHandler.class);

    private final AmqpContainerService service;
    private final ConcurrentHashMap<DataChannel, PipelineTransport> transportByChannel = new ConcurrentHashMap<>();

    public AmqpContainerChannelHandler(AmqpContainerService service) {
        this.service = service;
    }

    @Override
    public void onConnect(DataChannel channel) {
        LOG.debug("Client channel accepted by container");
        if (channel instanceof TcpDataChannel) {
            service.acceptConnection((TcpDataChannel) channel);
            var transport = new PipelineTransport(channel);
            transportByChannel.put(channel, transport);
            try {
                service.getContainer().handleConnection(transport);
            } catch (Exception e) {
                LOG.error("Failed to handle connection: {}", e.getMessage(), e);
                transportByChannel.remove(channel);
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
        LOG.debug("Client channel disconnected from container");
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.warn("Client channel error in container", cause);
        onDisconnect(channel);
    }
}
