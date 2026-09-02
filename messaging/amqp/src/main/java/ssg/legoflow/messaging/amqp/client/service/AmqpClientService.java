package ssg.legoflow.messaging.amqp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.transport.PipelineTransport;
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
import java.nio.channels.SelectionKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * AMQP 1.0 client service — delegates all I/O to the {@code SelectableChannelManager}.
 *
 * <p>Client-side TCP lifecycle:</p>
 * <ol>
 *   <li>{@code doConnect()} opens a non-blocking SocketChannel, registers for OP_CONNECT, THEN starts connect</li>
 *   <li>Manager fires {@code fireConnect()} when TCP connects</li>
 *   <li>Handler's {@code onConnect()} finishes TCP, enables OP_READ|OP_WRITE, runs protocol handshake</li>
 *   <li>Data flows via fireRead/fireWrite through pipeline to transport to protocol</li>
 * </ol>
 */
public final class AmqpClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpClientService.class);

    private final String host;
    private final int port;
    private final Duration timeout;
    private final int maxFrameSize;
    private final int channelMax;
    private final long idleTimeout;
    private final String username;
    private final String password;
    private final String brokerModeName;
    private final String containerId;

    private volatile AmqpClient client;
    private volatile PipelineTransport transport;
    private volatile TcpDataChannel dataChannel;
    private volatile Consumer<AmqpResult> deliveryCallback;

    void setClient(AmqpClient c) { this.client = c; }

    public record AmqpResult(boolean success, String address, ByteBuffer payload) {
        public static AmqpResult ok(String addr, ByteBuffer data) { return new AmqpResult(true, addr, data); }
        public static AmqpResult error(String msg) { return new AmqpResult(false, null, null); }
    }

    AmqpClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "AMQP Client Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
        this.timeout = builder.timeout;
        this.maxFrameSize = builder.maxFrameSize;
        this.channelMax = builder.channelMax;
        this.idleTimeout = builder.idleTimeout;
        this.username = builder.username;
        this.password = builder.password;
        this.brokerModeName = builder.brokerModeName;
        this.containerId = builder.containerId != null ? builder.containerId : "lego-flow-client";
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            // 1. Open non-blocking socket
            var socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            dataChannel = new TcpDataChannel(socketChannel);

            // 2. Create transport and handler with latch BEFORE registering
            transport = new PipelineTransport(dataChannel);
            var handler = new AmqpClientChannelHandler(this, ctx);
            var latch = new CountDownLatch(1);
            handler.setConnectLatch(latch);

            // 3. Register channel with manager (OP_CONNECT only) — handler + latch already in place
            ctx.registerChannel(this, dataChannel, handler);

            // 4. Start async connect AFTER registration — selector is already listening for OP_CONNECT
            socketChannel.connect(new InetSocketAddress(host, port));

            // 5. Wait for TCP connect (handler.onConnect fires on processing pool thread)
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("AMQP connection timeout: " + host + ":" + port);
            }

            // 6. Protocol handshake runs HERE on the calling thread — no deadlock with selector
            handler.doProtocolHandshake();

            LOG.info("AMQP client connected to {}:{} (selector-driven)", host, port);
        } catch (IOException e) {
            throw new RuntimeException("AMQP client failed to connect: " + host + ":" + port, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AMQP connection interrupted", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        try { if (transport != null) transport.close(); } catch (Exception ignored) {}
        if (ctx != null) {
            var mgr = ctx.getChannelManager();
            if (mgr != null) mgr.unregisterChannel(this);
        }
    }

    public AmqpClient getClient() { return client; }
    public PipelineTransport getTransport() { return transport; }
    public TcpDataChannel getDataChannel() { return dataChannel; }
    public void setDeliveryCallback(Consumer<AmqpResult> cb) { this.deliveryCallback = cb; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public Duration getTimeout() { return timeout; }
    public int getMaxFrameSize() { return maxFrameSize; }
    public int getChannelMax() { return channelMax; }
    public long getIdleTimeout() { return idleTimeout; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getBrokerModeName() { return brokerModeName; }
    public String getContainerId() { return containerId; }

    public ChannelHandler createChannelHandler() {
        return new AmqpClientChannelHandler(this, null);
    }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) { return new ByteBuffer[0]; }
    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "amqp-client";
        private final List<String> dependencies = new ArrayList<>();
        private int priority = 100;
        private Duration timeout = Duration.ofSeconds(10);
        private int maxFrameSize = Integer.MAX_VALUE;
        private int channelMax = 65535;
        private long idleTimeout = 0;
        private String username;
        private String password;
        private String brokerModeName = "STANDARD";
        private String containerId;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        /**
         * Set a unique service name. Each AMQP client instance registered with the same
         * {@link ssg.legoflow.service.manager.SelectableChannelManager} must have a
         * different name — the manager uses the name as the pipeline key.
         */
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { for (String dep : d) dependencies.add(dep); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public Builder timeout(Duration t) { this.timeout = t; return this; }
        public Builder maxFrameSize(int m) { this.maxFrameSize = m; return this; }
        public Builder channelMax(int c) { this.channelMax = c; return this; }
        public Builder idleTimeout(long ms) { this.idleTimeout = ms; return this; }
        public Builder username(String u) { this.username = u; return this; }
        public Builder password(String p) { this.password = p; return this; }
        public Builder brokerMode(BrokerMode mode) { this.brokerModeName = mode.name(); return this; }
        public Builder containerId(String id) { this.containerId = id; return this; }
        public AmqpClientService build() { return new AmqpClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
