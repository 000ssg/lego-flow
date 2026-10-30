package ssg.legoflow.xmpp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.client.XmppClientConfig;
import ssg.legoflow.xmpp.transport.PipelineXmppTransport;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * XMPP client service — delegates all I/O to the {@code SelectableChannelManager}.
 *
 * <p>Client-side TCP lifecycle (mirrors the NATS service form):</p>
 * <ol>
 *   <li>{@code doConnect()} opens a non-blocking SocketChannel, registers for OP_CONNECT,
 *       THEN starts the connect</li>
 *   <li>Manager fires {@code fireConnect()} when TCP connects</li>
 *   <li>Handler's {@code onConnect()} signals readiness; the protocol runs over the transport</li>
 *   <li>Data flows via fireRead/fireWrite through the pipeline to the transport to the protocol</li>
 * </ol>
 *
 * <p><b>Responsibility separation:</b> the socket channel is created, connected, and driven
 * here in the service layer (non-blocking, manager-owned); the protocol core ({@link XmppClient})
 * talks only to the {@link PipelineXmppTransport} — it never touches sockets or NIO.
 */
public final class XmppClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(XmppClientService.class);

    private final String host;
    private final int port;
    private final long timeoutMs;

    private volatile XmppClient client;
    private volatile PipelineXmppTransport transport;
    private volatile TcpDataChannel dataChannel;
    private volatile Consumer<XmppResult> stanzaCallback;
    private volatile Consumer<XmppResult> operationCallback;

    /** Result of an XMPP client operation. */
    public record XmppResult(boolean success, String stanzaType, ByteBuffer payload) {
        public static XmppResult ok(String type, ByteBuffer data) { return new XmppResult(true, type, data); }
        public static XmppResult error(String msg) { return new XmppResult(false, msg, null); }
    }

    XmppClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "XMPP Client Service",
                        builder.priority, builder.dependencies));
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
            transport = new PipelineXmppTransport(dataChannel);
            var handler = new XmppClientChannelHandler(this);
            var latch = new CountDownLatch(1);
            handler.setConnectLatch(latch);

            // 3. Register channel with the manager (OP_CONNECT only)
            ctx.registerChannel(this, dataChannel, handler);

            // 4. Start the async connect AFTER registration
            socketChannel.connect(new InetSocketAddress(host, port));

            // 5. Wait for the TCP connect (the manager fires fireConnect)
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("XMPP connection timeout: " + host + ":" + port);
            }

            // 6. Run the XMPP protocol over the transport
            var config = XmppClientConfig.builder(host, host)
                    .port(port)
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .enableTls(false)
                    .build();
            this.client = new XmppClient(transport);
            client.connect(config).join();
            LOG.info("XMPP client connected to {}:{}", host, port);
        } catch (IOException e) {
            throw new RuntimeException("XMPP client failed to connect: " + host + ":" + port, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("XMPP client connection interrupted", e);
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
        transitionTo(ProcessorState.STOPPED);
    }

    public XmppClient getClient() { return client; }
    public PipelineXmppTransport getTransport() { return transport; }
    public TcpDataChannel getDataChannel() { return dataChannel; }
    public void setStanzaCallback(Consumer<XmppResult> cb) { this.stanzaCallback = cb; }
    public void setOperationCallback(Consumer<XmppResult> cb) { this.operationCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInboundData(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    private void processInboundData(ByteBuffer data) {
        if (stanzaCallback != null) {
            stanzaCallback.accept(XmppResult.ok("xmpp", data.asReadOnlyBuffer()));
        }
    }

    public ChannelHandler createChannelHandler() { return new XmppClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "xmpp-client";
        private List<String> dependencies = List.of();
        private int priority = 100;
        private long timeoutMs = 10000;

        public Builder(String h, int p) { this.host = h; this.port = p; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public Builder timeoutMs(long t) { this.timeoutMs = t; return this; }
        public XmppClientService build() { return new XmppClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
