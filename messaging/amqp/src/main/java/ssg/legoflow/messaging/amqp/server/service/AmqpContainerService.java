package ssg.legoflow.messaging.amqp.server.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based AMQP container (broker) adapter for DP/DF composition. */
public final class AmqpContainerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final int port;
    private volatile ssg.legoflow.messaging.amqp.container.AmqpContainer container;
    private volatile Consumer<AmqpResult> messageCallback;

    public record AmqpResult(boolean success, String address, ByteBuffer payload) {
        public static AmqpResult ok(String addr, ByteBuffer data) { return new AmqpResult(true, addr, data); }
        public static AmqpResult error(String msg) { return new AmqpResult(false, null, null); }
    }

    AmqpContainerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "AMQP Container Service",
                        builder.priority, builder.dependencies));
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            var config = ssg.legoflow.messaging.amqp.container.ContainerConfig.defaults();
            this.container = new ssg.legoflow.messaging.amqp.container.AmqpContainer(config);
            container.start();
        } catch (Exception e) {
            throw new RuntimeException("AMQP container failed to start", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (container != null) { try { container.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.messaging.amqp.container.AmqpContainer getContainer() { return container; }
    public void setMessageCallback(Consumer<AmqpResult> cb) { this.messageCallback = cb; }

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
        if (messageCallback != null) messageCallback.accept(AmqpResult.ok("amqp", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new AmqpContainerChannelHandler(this); }

    public static class Builder {
        private final int port;
        private String name = "amqp-container";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(int port) { this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public AmqpContainerService build() { return new AmqpContainerService(this); }
    }

    public static Builder builder(int port) { return new Builder(port); }
}
