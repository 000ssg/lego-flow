package ssg.legoflow.messaging.stomp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.stomp.core.StompClient;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.transport.PipelineTransport;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * STOMP client service — delegates all I/O to the {@code SelectableChannelManager}.
 *
 * <p>Client-side TCP lifecycle:</p>
 * <ol>
 *   <li>{@code doConnect()} opens a non-blocking SocketChannel, registers for OP_CONNECT, THEN starts connect</li>
 *   <li>Manager fires {@code fireConnect()} when TCP connects</li>
 *   <li>Handler's {@code onConnect()} finishes TCP, enables OP_READ|OP_WRITE, runs protocol handshake</li>
 *   <li>Data flows via fireRead/fireWrite through pipeline to transport to protocol</li>
 * </ol>
 */
public final class StompClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(StompClientService.class);

    private final String host;
    private final int port;
    private final long timeoutMs;
    private final String login;
    private final String passcode;

    private volatile StompClient client;
    private volatile PipelineTransport transport;
    private volatile TcpDataChannel dataChannel;
    private volatile Consumer<StompFrame> messageCallback;

    void setClient(StompClient c) { this.client = c; }

    StompClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "STOMP Client Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
        this.timeoutMs = builder.timeoutMs;
        this.login = builder.login;
        this.passcode = builder.passcode;
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
            var handler = new StompClientChannelHandler(this, ctx);
            var latch = new CountDownLatch(1);
            handler.setConnectLatch(latch);

            // 3. Register channel with manager (OP_CONNECT only)
            ctx.registerChannel(this, dataChannel, handler);

            // 4. Start async connect AFTER registration
            socketChannel.connect(new InetSocketAddress(host, port));

            // 5. Wait for TCP connect
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("STOMP connection timeout: " + host + ":" + port);
            }

            // 6. Create client with transport and trigger STOMP connect
            this.client = new StompClient(transport);
            var connectedFrame = client.connect("/", login, passcode, 10000, 10000);

            LOG.info("STOMP client connected to {}:{}", host, port);
        } catch (IOException e) {
            throw new RuntimeException("STOMP client failed to connect: " + host + ":" + port, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("STOMP connection interrupted", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (client != null) {
            try { client.disconnect(); } catch (Exception ignored) {}
            try { client.close(); } catch (Exception ignored) {}
        }
        try { if (transport != null) transport.close(); } catch (Exception ignored) {}
        if (ctx != null) {
            var mgr = ctx.getChannelManager();
            if (mgr != null) mgr.unregisterChannel(this);
        }
        transitionTo(ProcessorState.STOPPED);
    }

    public StompClient getClient() { return client; }
    public PipelineTransport getTransport() { return transport; }
    public TcpDataChannel getDataChannel() { return dataChannel; }
    public void setMessageCallback(Consumer<StompFrame> cb) { this.messageCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) { return new ByteBuffer[0]; }
    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "stomp-client";
        private final List<String> dependencies = new ArrayList<>();
        private int priority = 100;
        private long timeoutMs = 10000;
        private String login;
        private String passcode;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { for (String dep : d) dependencies.add(dep); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public Builder timeoutMs(long t) { this.timeoutMs = t; return this; }
        public Builder login(String l) { this.login = l; return this; }
        public Builder passcode(String p) { this.passcode = p; return this; }
        public StompClientService build() { return new StompClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
