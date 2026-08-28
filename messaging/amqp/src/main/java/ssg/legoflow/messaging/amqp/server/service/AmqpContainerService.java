package ssg.legoflow.messaging.amqp.server.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.messaging.amqp.container.AmqpContainer;
import ssg.legoflow.messaging.amqp.container.ContainerConfig;
import ssg.legoflow.messaging.amqp.container.ContainerMode;
import ssg.legoflow.messaging.amqp.transport.PipelineTransport;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.TcpDataChannel;
import ssg.legoflow.service.channel.ServerDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * AMQP 1.0 container (server) service — listens through {@link ssg.legoflow.service.manager.SelectableChannelManager}.
 *
 * <p>Creates a {@link ServerSocketChannel}, wraps it in {@link ServerDataChannel},
 * registers it with the service manager via {@link ServiceContext#registerServerChannel(ServerDataChannel)}.
 * Accepted connections are wrapped in {@link TcpDataChannel}, registered, bridged to
 * {@link PipelineTransport}, and handed to {@link AmqpContainer#handleConnection}.
 */
public final class AmqpContainerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpContainerService.class);

    private final int port;
    private final String containerId;
    private final String host;
    private final int maxFrameSize;
    private final int channelMax;
    private final long idleTimeout;
    private final String modeName;
    private final boolean proto0Accepted;

    private volatile AmqpContainer container;
    volatile ServerDataChannel serverChannel;
    volatile List<PipelineTransport> clientTransports = new java.util.concurrent.CopyOnWriteArrayList<>();

    AmqpContainerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "AMQP Container Service",
                        builder.priority, builder.dependencies));
        this.port = builder.port;
        this.containerId = builder.containerId != null ? builder.containerId : "lego-flow-container";
        this.host = builder.host != null ? builder.host : "localhost";
        this.maxFrameSize = builder.maxFrameSize;
        this.channelMax = builder.channelMax;
        this.idleTimeout = builder.idleTimeout;
        this.modeName = builder.modeName;
        this.proto0Accepted = builder.proto0Accepted;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            ContainerMode mode = ContainerMode.valueOf(modeName);
            var config = ContainerConfig.builder()
                    .containerId(containerId)
                    .host(host)
                    .port(port)
                    .maxFrameSize(maxFrameSize)
                    .channelMax(channelMax)
                    .idleTimeout(idleTimeout)
                    .mode(mode)
                    .proto0Accepted(proto0Accepted)
                    .build();
            this.container = new AmqpContainer(config);

            // Create and bind server socket
            var serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(host, port));
            this.serverChannel = new ServerDataChannel(serverSocketChannel);

            // Register with service manager's selector
            ctx.registerServerChannel(this, serverChannel);

            // Start accept loop via service manager (selector-driven)
            container.start();
            LOG.info("AMQP container listening on port {}", port);
        } catch (IOException e) {
            throw new RuntimeException("AMQP container failed to start on port " + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (container != null) container.close(); } catch (Exception ignored) {}
        try { if (serverChannel != null) serverChannel.close(); } catch (Exception ignored) {}
    }

    /** Returns the underlying container (after connect). */
    public AmqpContainer getContainer() { return container; }

    /** Returns the port the service is listening on. */
    public int port() { return serverChannel != null ? serverChannel.getServerSocketChannel().socket().getLocalPort() : -1; }

    public void setMessageHandler(BiConsumer<AmqpContainer.ConnectionContext, AmqpContainer.IncomingMessage> handler) {
        if (container != null) container.messageHandler(handler);
    }

    /** Returns the number of active client connections. */
    public int clientCount() { return clientTransports.size(); }

    public ChannelHandler createChannelHandler() {
        return new AmqpContainerChannelHandler(this);
    }

    void acceptConnection(TcpDataChannel dataChannel) {
        try {
            var transport = new PipelineTransport(dataChannel);
            clientTransports.add(transport);
            LOG.info("AMQP connection accepted");
        } catch (Exception e) {
            LOG.error("Failed to prepare connection: {}", e.getMessage(), e);
            try { dataChannel.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) { return new ByteBuffer[0]; }
    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    public static class Builder {
        private String name = "amqp-container";
        private final List<String> dependencies = new ArrayList<>();
        private int priority = 100;
        private int port = 5672;
        private String containerId;
        private String host;
        private int maxFrameSize = Integer.MAX_VALUE;
        private int channelMax = 65535;
        private long idleTimeout = 0;
        private String modeName = "STANDARD";
        private boolean proto0Accepted = true;

        public Builder port(int p) { this.port = p; return this; }
        public Builder containerId(String id) { this.containerId = id; return this; }
        public Builder host(String h) { this.host = h; return this; }
        public Builder maxFrameSize(int m) { this.maxFrameSize = m; return this; }
        public Builder channelMax(int c) { this.channelMax = c; return this; }
        public Builder idleTimeout(long ms) { this.idleTimeout = ms; return this; }
        public Builder mode(ContainerMode mode) { this.modeName = mode.name(); return this; }
        public Builder proto0Accepted(boolean v) { this.proto0Accepted = v; return this; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { for (String dep : d) dependencies.add(dep); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public AmqpContainerService build() { return new AmqpContainerService(this); }
    }

    public static Builder builder() { return new Builder(); }
}
