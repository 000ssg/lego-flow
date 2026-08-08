package ssg.legoflow.coap.server.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/** Service-based CoAP server adapter for DP/DF composition. */
public final class CoapServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private volatile ssg.legoflow.coap.server.CoapServer server;
    private volatile Consumer<CoapResult> requestCallback;

    public record CoapResult(boolean success, String path, ByteBuffer payload) {
        public static CoapResult ok(String p, ByteBuffer data) { return new CoapResult(true, p, data); }
        public static CoapResult error(String msg) { return new CoapResult(false, null, null); }
    }

    CoapServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "CoAP Server Service",
                        builder.priority, builder.dependencies));
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.server = new ssg.legoflow.coap.server.CoapServer();
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("CoAP server failed to start", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (server != null) { try { server.stop(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.coap.server.CoapServer getServer() { return server; }
    public void setRequestCallback(Consumer<CoapResult> cb) { this.requestCallback = cb; }

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
        if (requestCallback != null) requestCallback.accept(CoapResult.ok("coap", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new CoapServerChannelHandler(this); }

    public static class Builder {
        private String name = "coap-server";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public CoapServerService build() { return new CoapServerService(this); }
    }

    public static Builder builder() { return new Builder(); }
}
