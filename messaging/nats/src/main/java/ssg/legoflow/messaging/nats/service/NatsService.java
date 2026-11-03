package ssg.legoflow.messaging.nats.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.transport.PipelineNatsTransport;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * NATS client service — delegates all I/O to the {@code SelectableChannelManager}.
 *
 * <p>Client-side TCP lifecycle (mirrors the STOMP reference form):</p>
 * <ol>
 *   <li>{@code doConnect()} opens a non-blocking SocketChannel, registers for OP_CONNECT,
 *       THEN starts the connect</li>
 *   <li>Manager fires {@code fireConnect()} when TCP connects</li>
 *   <li>Handler's {@code onConnect()} signals readiness; the protocol handshake runs</li>
 *   <li>Data flows via fireRead/fireWrite through the pipeline to the transport to the protocol</li>
 * </ol>
 *
 * <p><b>Responsibility separation:</b> the socket channel is created, connected, and driven
 * here in the service layer (non-blocking, manager-owned); the protocol core ({@link NatsClient})
 * talks only to the {@link PipelineNatsTransport} — it never touches sockets or NIO.
 */
public final class NatsService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(NatsService.class);

    private final String host;
    private final int port;
    private final long timeoutMs;

    private volatile NatsClient client;
    private volatile PipelineNatsTransport transport;
    private volatile TcpDataChannel dataChannel;
    private volatile java.util.function.Consumer<NatsMessage> messageCallback;
    private volatile java.util.function.Consumer<NatsResult> operationCallback;
    private volatile boolean connected = false;

    /** Result of a NATS send/subscribe operation. */
    public record NatsResult(boolean success, String subject, long msgId) {
        public static NatsResult ok(String s, long id) { return new NatsResult(true, s, id); }
        public static NatsResult error(String msg) { return new NatsResult(false, msg, -1); }
    }

    NatsService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "NATS Messaging Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
        this.timeoutMs = builder.timeoutMs;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            // 1. Open non-blocking socket channel
            var socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            dataChannel = new TcpDataChannel(socketChannel);

            // 2. Create transport + handler + connect latch BEFORE registering
            transport = new PipelineNatsTransport(dataChannel);
            var handler = new NatsClientChannelHandler(this);
            var latch = new CountDownLatch(1);
            handler.setConnectLatch(latch);

            // 3. Register channel with the manager (OP_CONNECT only)
            ctx.registerChannel(this, dataChannel, handler);

            // 4. Start the async connect AFTER registration
            socketChannel.connect(new InetSocketAddress(host, port));

            // 5. Wait for the TCP connect (the manager fires fireConnect)
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("NATS connection timeout: " + host + ":" + port);
            }

            // 6. Run the NATS protocol handshake over the transport
            this.client = new NatsClient(transport, ConnectOptions.withDefaults("nats-service"));
            client.connect();
            connected = true;
            LOG.info("NATS client connected to {}:{}", host, port);
        } catch (IOException e) {
            throw new RuntimeException("NATS client failed to connect: " + host + ":" + port, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("NATS client connection interrupted", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        connected = false;
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        try { if (transport != null) transport.close(); } catch (Exception ignored) {}
        if (ctx != null) {
            var mgr = ctx.getChannelManager();
            if (mgr != null) mgr.unregisterChannel(this);
        }
        transitionTo(ProcessorState.STOPPED);
    }

    public NatsClient getClient() { return client; }
    public PipelineNatsTransport getTransport() { return transport; }
    public TcpDataChannel getDataChannel() { return dataChannel; }
    public boolean isConnected() { return connected; }
    public void setMessageCallback(Consumer<NatsMessage> cb) { this.messageCallback = cb; }
    public void setOperationCallback(Consumer<NatsResult> cb) { this.operationCallback = cb; }

    public boolean subscribe(String subject, Consumer<NatsMessage> handler) {
        try {
            client.subscribe(subject, (msg) -> {
                if (messageCallback != null) messageCallback.accept(msg);
                handler.accept(msg);
            });
            return true;
        } catch (IOException e) {
            if (operationCallback != null) operationCallback.accept(NatsResult.error(e.getMessage()));
            return false;
        }
    }

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

    public ChannelHandler createChannelHandler() { return new NatsClientChannelHandler(this); }

    public static class Builder {
        private final String host; private final int port;
        private String name = "nats-client";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private long timeoutMs = 10000;

        public Builder(String h, int p) { this.host = h; this.port = p; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public Builder timeoutMs(long t) { this.timeoutMs = t; return this; }
        public NatsService build() { return new NatsService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
