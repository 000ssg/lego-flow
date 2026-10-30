package ssg.legoflow.messaging.stomp.server.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.messaging.stomp.core.StompBroker;
import ssg.legoflow.messaging.stomp.core.StompBrokerConfig;
import ssg.legoflow.messaging.stomp.core.StompEventListener;
import ssg.legoflow.messaging.stomp.transport.PipelineTransport;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ServerDataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * STOMP server service — listens through {@link ssg.legoflow.service.manager.SelectableChannelManager}.
 *
 * <p>Creates a {@link ServerSocketChannel}, wraps it in {@link ServerDataChannel},
 * registers it with the service manager via {@link ServiceContext#registerServerChannel}.
 * Accepted connections are wrapped in {@link PipelineTransport} and handed to {@link StompBroker#accept}.
 */
public final class StompServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(StompServerService.class);

    private final int port;
    private final String host;
    private final StompBrokerConfig config;

    private volatile StompBroker broker;
    volatile ServerDataChannel serverChannel;

    StompServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "STOMP Server Service",
                        builder.priority, builder.dependencies));
        this.port = builder.port;
        this.host = builder.host != null ? builder.host : "localhost";
        this.config = builder.config != null ? builder.config : StompBrokerConfig.defaults();
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            // Create and bind server socket
            var serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(host, port));
            this.serverChannel = new ServerDataChannel(serverSocketChannel);

            // Register with service manager's selector
            ctx.registerServerChannel(this, serverChannel);

            // Add handler to the server pipeline so accepted connections are dispatched
            var pipeline = ctx.getChannelManager().getChannelPipeline(this);
            if (pipeline != null) {
                pipeline.addLast(createChannelHandler());
            }

            // Create and start broker
            this.broker = new StompBroker(config);
            LOG.info("STOMP broker listening on port {}", port);
        } catch (Exception e) {
            throw new RuntimeException("STOMP broker failed to start on " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (broker != null) broker.close(); } catch (Exception ignored) {}
        try {
            var mgr = ctx.getChannelManager();
            if (mgr != null) mgr.unregisterServerChannel(this);
        } catch (Exception ignored) {}
        try { if (serverChannel != null) serverChannel.close(); } catch (Exception ignored) {}
    }

    /** Returns the underlying broker (after connect). */
    public StompBroker getBroker() { return broker; }

    /** Returns the port the service is listening on. */
    public int port() {
        return serverChannel != null ? serverChannel.getServerSocketChannel().socket().getLocalPort() : -1;
    }

    /** Sets the protocol event listener. */
    public void setListener(StompEventListener listener) {
        if (broker != null) broker.setListener(listener);
    }

    public ChannelHandler createChannelHandler() {
        return new StompServerChannelHandler(this);
    }

    /** Returns this service for handler lookup. */
    public StompServerService getService() { return this; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) { return new ByteBuffer[0]; }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    public static class Builder {
        private String name = "stomp-server";
        private final List<String> dependencies = new ArrayList<>();
        private int priority = 100;
        private int port = 61613;
        private String host;
        private StompBrokerConfig config;

        public Builder port(int p) { this.port = p; return this; }
        public Builder host(String h) { this.host = h; return this; }
        public Builder config(StompBrokerConfig c) { this.config = c; return this; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { for (String dep : d) dependencies.add(dep); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public StompServerService build() { return new StompServerService(this); }
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(String host, int port) { return new Builder().host(host).port(port); }
}
