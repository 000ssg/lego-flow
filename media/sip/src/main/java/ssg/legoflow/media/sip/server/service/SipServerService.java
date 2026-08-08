package ssg.legoflow.media.sip.server.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/** Service-based SIP server adapter for DP/DF composition. */
public final class SipServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final int port;
    private volatile ssg.legoflow.media.sip.server.SipServer server;
    private volatile Consumer<SipResult> messageCallback;

    public record SipResult(boolean success, String method, ByteBuffer payload) {
        public static SipResult ok(String m, ByteBuffer data) { return new SipResult(true, m, data); }
        public static SipResult error(String msg) { return new SipResult(false, null, null); }
    }

    SipServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "SIP Server Service",
                        builder.priority, builder.dependencies));
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.server = new ssg.legoflow.media.sip.server.SipServer(port);
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("SIP server failed to start on port " + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (server != null) { try { server.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.media.sip.server.SipServer getServer() { return server; }
    public void setMessageCallback(Consumer<SipResult> cb) { this.messageCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    private void processInbound(ByteBuffer data) {
        if (messageCallback != null) messageCallback.accept(SipResult.ok("sip", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new SipServerChannelHandler(this); }

    public static class Builder {
        private final int port;
        private String name = "sip-server";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(int port) { this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public SipServerService build() { return new SipServerService(this); }
    }

    public static Builder builder(int port) { return new Builder(port); }
}
