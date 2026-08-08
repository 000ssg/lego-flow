package ssg.legoflow.messaging.amqp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/** Service-based AMQP client adapter for DP/DF composition. */
public final class AmqpClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.messaging.amqp.client.AmqpClient client;
    private volatile Consumer<AmqpResult> deliveryCallback;

    public record AmqpResult(boolean success, String address, ByteBuffer payload) {
        public static AmqpResult ok(String addr, ByteBuffer data) { return new AmqpResult(true, addr, data); }
        public static AmqpResult error(String msg) { return new AmqpResult(false, null, null); }
    }

    AmqpClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "AMQP Client Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            var config = ssg.legoflow.messaging.amqp.client.ClientConfig.builder()
                    .host(host).port(port).build();
            this.client = new ssg.legoflow.messaging.amqp.client.AmqpClient(config);
            client.connect();
        } catch (Exception e) {
            throw new RuntimeException("AMQP client failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (client != null) { try { client.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.messaging.amqp.client.AmqpClient getClient() { return client; }
    public void setDeliveryCallback(Consumer<AmqpResult> cb) { this.deliveryCallback = cb; }

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
        if (deliveryCallback != null) deliveryCallback.accept(AmqpResult.ok("delivery", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new AmqpClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "amqp-client";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public AmqpClientService build() { return new AmqpClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
