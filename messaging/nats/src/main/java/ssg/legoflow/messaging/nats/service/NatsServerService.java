package ssg.legoflow.messaging.nats.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.nats.server.NatsServer;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ServerDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.util.function.Consumer;

/**
 * NATS server service — listens through the {@code SelectableChannelManager}.
 *
 * <p>Creates a {@link ServerSocketChannel}, wraps it in {@link ServerDataChannel}, and
 * registers it with the service manager via {@link ServiceContext#registerServerChannel}.
 * Accepted connections are wrapped in a {@link ssg.legoflow.messaging.nats.transport.PipelineNatsTransport}
 * and handed to {@link NatsServer#handleConnection} (mirrors the STOMP server-service form).
 *
 * <p><b>Responsibility separation:</b> the listening socket is created, bound, and driven
 * here in the service layer (manager-owned); the protocol core ({@link NatsServer}) is
 * headless — it has no accept loop and never touches sockets or NIO.
 */
public final class NatsServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(NatsServerService.class);

    private final int port;
    private final String host;

    private volatile NatsServer server;
    private volatile ServerDataChannel serverChannel;
    private volatile Consumer<NatsResult> publishCallback;

    /** Result of a NATS server operation. */
    public record NatsResult(boolean success, String subject) {
        public static NatsResult ok(String s) { return new NatsResult(true, s); }
        public static NatsResult error(String msg) { return new NatsResult(false, msg); }
    }

    NatsServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "NATS Server Service", builder.priority, builder.dependencies));
        this.port = builder.port;
        this.host = builder.host != null ? builder.host : "localhost";
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            // Create and bind the server socket channel
            var serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(host, port));
            this.serverChannel = new ServerDataChannel(serverSocketChannel);

            // Register with the manager's selector (OP_ACCEPT)
            ctx.registerServerChannel(this, serverChannel);

            // Add a handler to the server pipeline so accepted connections are dispatched
            var pipeline = ctx.getChannelManager().getChannelPipeline(this);
            if (pipeline != null) {
                pipeline.addLast(createChannelHandler());
            }

            // Create and start the (headless) core
            this.server = new NatsServer(0);
            server.start();
            LOG.info("NATS server listening on port {}", getPort());
        } catch (Exception e) {
            throw new RuntimeException("NATS server failed to start on " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        try {
            var mgr = ctx.getChannelManager();
            if (mgr != null) mgr.unregisterServerChannel(this);
        } catch (Exception ignored) {}
        try { if (serverChannel != null) serverChannel.close(); } catch (Exception ignored) {}
        transitionTo(ProcessorState.STOPPED);
    }

    /** Returns the core server (after connect). */
    public NatsServer getServer() { return server; }

    /** Returns the port the service is listening on. */
    public int getPort() {
        return serverChannel != null
                ? serverChannel.getServerSocketChannel().socket().getLocalPort()
                : -1;
    }

    public void setPublishCallback(Consumer<NatsResult> cb) { this.publishCallback = cb; }

    /**
     * Converts input to output. In the service layer, this method is intentionally empty
     * because all data flow is handled through the transport pipeline via {@code ChannelHandler}
     * callbacks ({@code onRead}, {@code onWrite}). The service layer owns the socket lifecycle
     * and delegates all protocol processing to the transport and core classes.
     */
    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) { return new ByteBuffer[0]; }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    public ChannelHandler createChannelHandler() { return new NatsServerChannelHandler(this); }

    public static class Builder {
        private String name = "nats-server";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private int port = 4222;
        private String host;

        public Builder port(int p) { this.port = p; return this; }
        public Builder host(String h) { this.host = h; return this; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public NatsServerService build() { return new NatsServerService(this); }
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(String host, int port) { return new Builder().host(host).port(port); }
}
