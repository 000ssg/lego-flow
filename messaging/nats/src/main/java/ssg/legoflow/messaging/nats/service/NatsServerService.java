package ssg.legoflow.messaging.nats.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.nats.server.NatsServer;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
/**
 * Service-based NATS server adapter for composition within the service framework.
 * Wraps NatsServer to enable NATS broker operations through DP/DF pipeline.
 */
public final class NatsServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private volatile NatsServer server;
    private volatile Consumer<NatsResult> publishCallback;

    /** Result of a NATS server operation. */
    public record NatsResult(boolean success, String subject) {
        public static NatsResult ok(String s) { return new NatsResult(true, s); }
        public static NatsResult error(String msg) { return new NatsResult(false, msg); }
    }

    NatsServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "NATS Server Service", builder.priority, builder.dependencies));
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            server = new NatsServer();
            server.start(0); // Bind to random port
        } catch (IOException e) {
            throw new RuntimeException("NATS server failed to start", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    public NatsServer getServer() { return server; }
    public int getPort() { return server != null ? server.port() : -1; }
    public void setPublishCallback(Consumer<NatsResult> cb) { this.publishCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInboundData(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        for (ByteBuffer buf : output) {
            try { if (buf != null && buf.hasRemaining()) processOutboundData(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    private void processInboundData(ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        String subject = "default";
        int idx = content.indexOf('\n');
        if (idx > 0) subject = content.substring(0, idx).trim();
        // Server-side message processing - relay to clients via NatsServer
        if (publishCallback != null) publishCallback.accept(NatsResult.ok(subject));
    }

    private void processOutboundData(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new NatsServerChannelHandler(this); }

    public static class Builder {
        private String name = "nats-server";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;

        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public NatsServerService build() { return new NatsServerService(this); }
    }

    public static Builder builder() { return new Builder(); }
}
