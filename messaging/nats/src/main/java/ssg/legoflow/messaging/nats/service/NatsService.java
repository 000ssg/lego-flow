package ssg.legoflow.messaging.nats.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Service-based NATS adapter for composition within the service framework. */
public final class NatsService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    
    private volatile NatsClient client;
    private final Map<String, Consumer<NatsMessage>> subscriptions = new ConcurrentHashMap<>();
    private volatile java.util.function.Consumer<NatsMessage> messageCallback;
    private volatile java.util.function.Consumer<NatsResult> operationCallback;

    /** Result of a NATS send/subscribe operation. */
    public record NatsResult(boolean success, String subject, long msgId) {
        public static NatsResult ok(String s, long id) { return new NatsResult(true, s, id); }
        public static NatsResult error(String msg) { return new NatsResult(false, msg, -1); }
    }

    NatsService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "NATS Messaging Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            client = new NatsClient(host, port);
            client.connect();
        } catch (IOException e) {
            throw new RuntimeException("NATS connection failed: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    public NatsClient getClient() { return client; }
    public void setMessageCallback(Consumer<NatsMessage> cb) { this.messageCallback = cb; }
    public void setOperationCallback(Consumer<NatsResult> cb) { this.operationCallback = cb; }

    public boolean subscribe(String subject, Consumer<NatsMessage> handler) {
        try {
            client.subscribe(subject, (msg) -> {
                subscriptions.putIfAbsent(subject, handler);
                if (messageCallback != null) messageCallback.accept(msg);
            });
            return true;
        } catch (IOException e) {
            if (operationCallback != null) operationCallback.accept(NatsResult.error(e.getMessage()));
            return false;
        }
    }

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
        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            String subject = "default";
            int idx = content.indexOf('\n');
            if (idx > 0) {
                subject = content.substring(0, idx).trim();
                bytes = content.substring(idx + 1).getBytes(StandardCharsets.UTF_8);
            }
            client.publish(subject, bytes);
            if (operationCallback != null) operationCallback.accept(NatsResult.ok(subject, -1));
        } catch (Exception e) {
            if (operationCallback != null) operationCallback.accept(NatsResult.error(e.getMessage()));
        }
    }

    private void processOutboundData(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new NatsClientChannelHandler(this); }

    public static class Builder {
        private final String host; private final int port;
        private String name = "nats-client";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;

        public Builder(String h, int p) { this.host = h; this.port = p; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public NatsService build() { return new NatsService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
