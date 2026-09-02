package ssg.legoflow.messaging.amqp.client.service;

import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.transport.PipelineTransport;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;

/**
 * Pipeline handler for AMQP client connections.
 *
 * <p>TCP connect → onConnect() → finishes TCP, enables OP_READ|OP_WRITE.
 * Protocol handshake runs on the SELECTOR thread (where connect() is called),
 * not on the processing pool thread (where onConnect fires).</p>
 */
public final class AmqpClientChannelHandler implements ChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpClientChannelHandler.class);

    private final AmqpClientService service;
    private final ServiceContext ctx;
    private AmqpClient client;
    private PipelineTransport transport;
    private volatile CountDownLatch connectLatch;

    public AmqpClientChannelHandler(AmqpClientService service, ServiceContext ctx) {
        this.service = service;
        this.ctx = ctx;
    }

    void setConnectLatch(CountDownLatch latch) {
        this.connectLatch = latch;
    }

    /**
     * Called by the manager when TCP connect completes.
     * Finishes TCP, enables READ|WRITE, and signals the latch.
     * Protocol handshake is handled by the calling thread (doConnect).
     */
    @Override
    public void onConnect(DataChannel channel) {
        try {
            // Finish the TCP connect (required for non-blocking connect)
            var socketChannel = ((TcpDataChannel) channel).getSocketChannel();
            socketChannel.finishConnect();

            // Enable data flow — switch from OP_CONNECT to OP_READ|OP_WRITE
            var mgr = ctx.getChannelManager();
            if (mgr != null) {
                mgr.updateChannelOps(service, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }

            // Signal TCP connected — caller will do protocol handshake
            if (connectLatch != null) connectLatch.countDown();
            LOG.debug("TCP connected for {}", service.getDescriptor().name());
        } catch (Exception e) {
            LOG.error("TCP connect failed for {}: {}", service.getDescriptor().name(), e.getMessage(), e);
            if (connectLatch != null) connectLatch.countDown();
            throw new RuntimeException(e);
        }
    }

    /**
     * Perform the AMQP protocol handshake after TCP connect completes.
     * Called by the service AFTER the latch fires, on the service's calling thread.
     */
    void doProtocolHandshake() {
        try {
            var configBuilder = ClientConfig.builder()
                    .host(service.getHost())
                    .port(service.getPort())
                    .containerId(service.getContainerId())
                    .maxFrameSize(service.getMaxFrameSize())
                    .channelMax(service.getChannelMax())
                    .idleTimeout(service.getIdleTimeout())
                    .connectTimeout(service.getTimeout());
            if (service.getUsername() != null) configBuilder.username(service.getUsername()).password(service.getPassword());
            if (service.getBrokerModeName() != null) configBuilder.brokerMode(BrokerMode.valueOf(service.getBrokerModeName()));

            this.client = new AmqpClient(configBuilder.build());
            this.transport = service.getTransport();
            client.connect(transport);
            service.setClient(client);
            LOG.debug("AMQP protocol handshake completed for {}", service.getDescriptor().name());
        } catch (Exception e) {
            LOG.error("AMQP protocol handshake failed for {}: {}", service.getDescriptor().name(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        System.out.println("[handler.onRead] transport=" + (transport != null) + " data=" + data.remaining());
        if (transport != null) transport.onRead(channel, data);
    }

    @Override
    public void onWrite(DataChannel channel) {
        if (transport != null) transport.onWrite(channel);
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        if (transport != null) transport.close();
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        LOG.debug("AMQP client disconnected: {}", service.getDescriptor().name());
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.error("AMQP client error: {}", service.getDescriptor().name(), cause);
    }
}
