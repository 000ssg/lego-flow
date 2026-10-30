package ssg.legoflow.messaging.kafka.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.kafka.transport.PipelineKafkaTransport;
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

/**
 * Kafka client service — delegates all I/O to the {@code SelectableChannelManager}.
 *
 * <p>Client-side TCP lifecycle (mirrors the NATS/STOMP reference form):</p>
 * <ol>
 *   <li>{@code doConnect()} opens a non-blocking SocketChannel, registers for OP_CONNECT,
 *       THEN starts the connect.</li>
 *   <li>The manager fires {@code fireConnect()} when TCP connects.</li>
 *   <li>The handler's {@code onConnect()} signals readiness.</li>
 *   <li>Data flows via fireRead/fireWrite through the pipeline to the transport to the
 *       protocol core.</li>
 * </ol>
 *
 * <p><b>Responsibility separation:</b> the socket channel is created, connected, and driven
 * here in the service layer (non-blocking, manager-owned); the protocol core
 * ({@link ssg.legoflow.messaging.kafka.client.KafkaConnection}) talks only to the
 * {@link PipelineKafkaTransport} — it never touches sockets or NIO.
 *
 * <p>The service owns the transport and exposes it so callers construct the appropriate
 * client ({@code KafkaProducer}/{@code KafkaConsumer}/{@code KafkaAdminClient}) over it.
 * A real TCP connection is established before {@code doConnect()} returns, so the returned
 * transport is already open and ready for request/response framing.
 */
public final class KafkaClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaClientService.class);

    private final String host;
    private final int port;
    private final long timeoutMs;

    private volatile PipelineKafkaTransport transport;
    private volatile TcpDataChannel dataChannel;

    KafkaClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "Kafka Client Service", builder.priority, builder.dependencies));
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
            transport = new PipelineKafkaTransport(dataChannel);
            var handler = new KafkaClientChannelHandler(this);
            var latch = new CountDownLatch(1);
            handler.setConnectLatch(latch);

            // 3. Register channel with the manager (OP_CONNECT only)
            ctx.registerChannel(this, dataChannel, handler);

            // 4. Start the async connect AFTER registration
            socketChannel.connect(new InetSocketAddress(host, port));

            // 5. Wait for the TCP connect (the manager fires fireConnect)
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("Kafka client connection timeout: " + host + ":" + port);
            }
            LOG.info("Kafka client connected to {}:{}", host, port);
        } catch (IOException e) {
            throw new RuntimeException("Kafka client failed to connect: " + host + ":" + port, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Kafka client connection interrupted", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (transport != null) transport.close(); } catch (Exception ignored) {}
        if (ctx != null) {
            var mgr = ctx.getChannelManager();
            if (mgr != null) mgr.unregisterChannel(this);
        }
        transitionTo(ProcessorState.STOPPED);
    }

    /** Returns the open transport (after connect); callers construct a client over it. */
    public PipelineKafkaTransport getTransport() { return transport; }
    public TcpDataChannel getDataChannel() { return dataChannel; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining() && transport != null) transport.send(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        return new ByteBuffer[0];
    }

    public ChannelHandler createChannelHandler() { return new KafkaClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "kafka-client";
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
        public KafkaClientService build() { return new KafkaClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
