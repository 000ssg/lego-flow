package ssg.legoflow.messaging.amqp.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.messaging.amqp.transport.PipelineTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;

/**
 * Channel handler for {@link AmqpClientService}. Bridges the service pipeline
 * to the AMQP client via {@link PipelineTransport}.
 */
public final class AmqpClientChannelHandler implements ChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpClientChannelHandler.class);

    private final AmqpClientService service;
    volatile PipelineTransport transport;

    public AmqpClientChannelHandler(AmqpClientService service) {
        this.service = service;
    }

    @Override
    public void onConnect(DataChannel channel) {
        // Connection established — the transport is already wired in doConnect()
        LOG.debug("Client channel connected");
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        // Data read from socket — deliver to the protocol transport
        // The PipelineTransport's onRead buffers data for the AMQP client's readLoop
        if (transport != null) {
            transport.onRead(channel, data);
        }
    }

    @Override
    public void onWrite(DataChannel channel) {
        if (transport != null) {
            transport.onWrite(channel);
        }
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        LOG.debug("Client channel disconnected");
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.warn("Client channel error", cause);
    }
}
