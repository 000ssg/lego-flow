package ssg.legoflow.messaging.kafka.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.broker.storage.LogStorageFactory;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ServerDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;

/**
 * Kafka broker service — listens through the {@code SelectableChannelManager}.
 *
 * <p>Creates a {@link ServerSocketChannel}, wraps it in {@link ServerDataChannel}, and
 * registers it with the service manager via {@link ServiceContext#registerServerChannel}.
 * Accepted connections are wrapped in a {@link ssg.legoflow.messaging.kafka.transport.PipelineKafkaTransport}
 * and handed to {@link KafkaBroker#handleConnection} (mirrors the NATS/STOMP server-service form).
 *
 * <p><b>Responsibility separation:</b> the listening socket is created, bound, and driven
 * here in the service layer (manager-owned); the protocol core ({@link KafkaBroker}) is
 * headless — it has no accept loop and never touches sockets or NIO.
 */
public final class KafkaBrokerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBrokerService.class);

    private final int port;
    private final String host;
    private final int brokerId;
    private final int defaultPartitions;
    private final LogStorageFactory storageFactory;

    private volatile KafkaBroker broker;
    private volatile ServerDataChannel serverChannel;

    KafkaBrokerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "Kafka Broker Service", builder.priority, builder.dependencies));
        this.port = builder.port;
        this.host = builder.host != null ? builder.host : "localhost";
        this.brokerId = builder.brokerId;
        this.defaultPartitions = builder.defaultPartitions;
        this.storageFactory = builder.storageFactory != null ? builder.storageFactory : LogStorageFactory.inMemory();
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            // Create and bind the server socket channel
            var serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(host, port));
            this.serverChannel = new ServerDataChannel(serverSocketChannel);

            // Register with the manager's selector (OP_ACCEPT)
            ctx.registerServerChannel(this, serverChannel);

            // Add a handler to the server pipeline so accepted connections are dispatched
            var pipeline = ctx.getChannelManager().getChannelPipeline(this);
            if (pipeline != null) {
                pipeline.addLast(createChannelHandler());
            }

            // Create and start the (headless) core; tell it the real bound port
            this.broker = new KafkaBroker(host, port, brokerId, defaultPartitions, storageFactory);
            broker.setBoundPort(getPort());
            broker.start();
            LOG.info("Kafka broker service listening on port {}", getPort());
        } catch (Exception e) {
            throw new RuntimeException("Kafka broker service failed to start on " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (broker != null) broker.close(); } catch (Exception ignored) {}
        if (ctx != null) {
            try {
                var mgr = ctx.getChannelManager();
                if (mgr != null) mgr.unregisterServerChannel(this);
            } catch (Exception ignored) {}
        }
        try { if (serverChannel != null) serverChannel.close(); } catch (Exception ignored) {}
        transitionTo(ProcessorState.STOPPED);
    }

    /** Returns the core broker (after connect). */
    public KafkaBroker getBroker() { return broker; }

    /** Returns the port the service is listening on. */
    public int getPort() {
        return serverChannel != null
                ? serverChannel.getServerSocketChannel().socket().getLocalPort()
                : -1;
    }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        return new ByteBuffer[0];
    }

    public ChannelHandler createChannelHandler() { return new KafkaBrokerChannelHandler(this); }

    public static class Builder {
        private String name = "kafka-broker";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private int port = 9092;
        private String host;
        private int brokerId = 0;
        private int defaultPartitions = 1;
        private LogStorageFactory storageFactory;

        public Builder port(int p) { this.port = p; return this; }
        public Builder host(String h) { this.host = h; return this; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder brokerId(int id) { this.brokerId = id; return this; }
        public Builder defaultPartitions(int p) { this.defaultPartitions = p; return this; }
        public Builder storageFactory(LogStorageFactory f) { this.storageFactory = f; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public KafkaBrokerService build() { return new KafkaBrokerService(this); }
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(String host, int port) { return new Builder().host(host).port(port); }
}
