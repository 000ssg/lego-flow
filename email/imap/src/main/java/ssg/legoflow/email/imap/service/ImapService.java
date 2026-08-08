package ssg.legoflow.email.imap.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Service-based IMAP adapter that wraps the existing IMAP protocol implementation
 * as a {@link ssg.legoflow.service.Service} for composition within the service framework.
 *
 * <p>Data flows through the DP/DF pipeline where inbound data from a DataChannel
 * is processed by the IMAP connection layer and dispatched to message handlers.
 */
public final class ImapService extends AbstractService<ByteBuffer, ByteBuffer> {

    public enum Mode { CLIENT, SERVER }

    private final InetSocketAddress bindAddress;
    private final Mode mode;
    
    private volatile ssg.legoflow.email.imap.server.ImapServer server;
    private volatile java.util.function.Consumer<ByteBuffer> messageCallback;

    ImapService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "IMAP Protocol Service", builder.priority, builder.dependencies));
        this.bindAddress = builder.bindAddress;
        this.mode = builder.mode;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            if (mode == Mode.SERVER) {
                var addr = bindAddress != null ? bindAddress : new InetSocketAddress("0.0.0.0", 143);
                var store = new ssg.legoflow.email.imap.server.InMemoryMailStore();
                this.server = new ssg.legoflow.email.imap.server.ImapServer(
                    addr.getHostName(), addr.getPort(), store);
                server.start();
            }
        } catch (Exception e) {
            throw new RuntimeException("IMAP service failed to connect: " + bindAddress, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (server != null) server.close();
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.email.imap.server.ImapServer getServer() { return server; }

    public void setMessageCallback(java.util.function.Consumer<ByteBuffer> cb) { this.messageCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        for (ByteBuffer buf : output) {
            try { if (buf != null && buf.hasRemaining()) processOutbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    private void processInbound(ByteBuffer data) {
        if (messageCallback != null) messageCallback.accept(data.asReadOnlyBuffer());
    }

    private void processOutbound(ByteBuffer data) { /* outbound through IMAP transport */ }

    public ChannelHandler createChannelHandler() { return new ImapChannelHandler(this); }

    public static class Builder {
        private InetSocketAddress bindAddress;
        private String name = "imap";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private Mode mode = Mode.CLIENT;

        public Builder(String host, int port) { this.bindAddress = new InetSocketAddress(host, port); }
        public Builder name(String n) { this.name = n; return this; }
        public Builder mode(Mode m) { this.mode = m; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public ImapService build() { return new ImapService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
