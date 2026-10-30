package ssg.legoflow.xmpp.server.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.xmpp.server.XmppServer;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ServerDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/**
 * XMPP server service — listens through the {@code SelectableChannelManager}.
 *
 * <p>Creates a {@link ServerDataChannel}, registers it with the service manager via
 * {@link ServiceContext#registerServerChannel}. Accepted connections are wrapped in a
 * {@link ssg.legoflow.xmpp.transport.PipelineXmppTransport} by the channel handler and
 * handed to {@link XmppServer#handleConnection} — mirrors the NATS/STOMP server-service form.
 *
 * <p><b>Responsibility separation:</b> the listening socket is created, bound, and driven
 * here in the service layer (manager-owned, non-blocking); the protocol core
 * ({@link XmppServer}) is headless — it has no accept loop and never touches sockets or NIO.
 */
public final class XmppServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(XmppServerService.class);

    private final String host;
    private final int port;

    private volatile XmppServer server;
    private volatile ServerDataChannel serverChannel;
    private volatile Consumer<XmppResult> stanzaCallback;

    /** Result of an XMPP server operation. */
    public record XmppResult(boolean success, String stanzaType, ByteBuffer payload) {
        public static XmppResult ok(String type, ByteBuffer data) { return new XmppResult(true, type, data); }
        public static XmppResult error(String msg) { return new XmppResult(false, null, null); }
    }

    XmppServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "XMPP Server Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host != null ? builder.host : "localhost";
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            var serverDataChannel = new ServerDataChannel(java.nio.channels.ServerSocketChannel.open());
            serverDataChannel.getServerSocketChannel()
                    .bind(new java.net.InetSocketAddress(host, port));
            this.serverChannel = serverDataChannel;

            ctx.registerServerChannel(this, serverDataChannel);

            var pipeline = ctx.getChannelManager().getChannelPipeline(this);
            if (pipeline != null) {
                pipeline.addLast(createChannelHandler());
            }

            this.server = new XmppServer(port);
            server.start();
            LOG.info("XMPP server listening on port {}", getPort());
        } catch (Exception e) {
            throw new RuntimeException("XMPP server failed to start on " + host + ":" + port, e);
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

    /** Returns the headless core server (after connect). */
    public XmppServer getServer() { return server; }

    /** Returns the port the service is actually listening on (-1 before connect). */
    public int getPort() {
        return serverChannel != null
                ? serverChannel.getServerSocketChannel().socket().getLocalPort()
                : port;
    }

    public void setStanzaCallback(Consumer<XmppResult> cb) { this.stanzaCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    private void processInbound(ByteBuffer data) {
        if (stanzaCallback != null) {
            stanzaCallback.accept(XmppResult.ok("xmpp", data.asReadOnlyBuffer()));
        }
    }

    public ChannelHandler createChannelHandler() { return new XmppServerChannelHandler(this); }

    public static class Builder {
        private String name = "xmpp-server";
        private List<String> dependencies = List.of();
        private int priority = 100;
        private int port = 5222;
        private String host;

        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public Builder port(int p) { this.port = p; return this; }
        public Builder host(String h) { this.host = h; return this; }
        public XmppServerService build() { return new XmppServerService(this); }
    }

    public static Builder builder(int port) { return new Builder().port(port); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(String host, int port) { return new Builder().host(host).port(port); }
}
