package ssg.legoflow.messaging.amqp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.transport.PipelineTransport;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.TcpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AMQP 1.0 client service — creates a TCP connection via virtual threads.
 *
 * <p>Opens a {@link SocketChannel}, wraps it in {@link TcpDataChannel}, and bridges to
 * {@link AmqpClient} via {@link PipelineTransport}. The protocol layer's readLoop runs
 * on a virtual thread and blocks on {@code receive()} — virtual threads make this efficient
 * with zero selector overhead.
 *
 * <p>Only the server side needs {@link ssg.legoflow.service.manager.SelectableChannelManager}
 * for multiplexing many connections. A client has one connection and a virtual-threaded
 * readLoop is the natural choice.
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
            // Create TCP socket channel and connect
            var socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(host, port));
            socketChannel.configureBlocking(true);

            dataChannel = new TcpDataChannel(socketChannel);
            transport = new PipelineTransport(dataChannel);

            // Build config
            var configBuilder = ClientConfig.builder()
                    .host(host)
                    .port(port)
                    .containerId(containerId)
                    .maxFrameSize(maxFrameSize)
                    .channelMax(channelMax)
                    .idleTimeout(idleTimeout)
                    .connectTimeout(timeout);
            if (username != null) configBuilder.username(username).password(password);
            if (brokerModeName != null) configBuilder.brokerMode(BrokerMode.valueOf(brokerModeName));
            var config = configBuilder.build();

            this.client = new AmqpClient(config);
            client.connect(transport);

            LOG.info("AMQP client connected to {}:{}", host, port);
        } catch (IOException e) {
            throw new RuntimeException("AMQP client failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        try { if (transport != null) transport.close(); } catch (Exception ignored) {}
    }

    public AmqpClient getClient() { return client; }
    public PipelineTransport getTransport() { return transport; }
    public TcpDataChannel getDataChannel() { return dataChannel; }
    public void setDeliveryCallback(Consumer<AmqpResult> cb) { this.deliveryCallback = cb; }

    public ChannelHandler createChannelHandler() {
        return new AmqpClientChannelHandler(this);
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
