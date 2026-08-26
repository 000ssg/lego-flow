package ssg.legoflow.messaging.amqp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.amqp.common.AmqpContext;
import ssg.legoflow.messaging.amqp.common.AmqpCtxImpl;
import ssg.legoflow.messaging.amqp.transport.AmqpFrameCodec;
import ssg.legoflow.messaging.amqp.transport.AmqpFrameCodecImpl;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.manager.SelectableChannelManager;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.function.Consumer;
/** Service-based AMQP client adapter for DP/DF composition. */
public final class AmqpClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile Consumer<AmqpResult> deliveryCallback;
    private volatile SelectableChannelManager channelManager;
    private volatile AmqpContext amqpContext;

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

    public void setChannelManager(SelectableChannelManager cm) {
        this.channelManager = cm;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            if (channelManager == null) {
                throw new IllegalStateException("SelectableChannelManager not set");
            }
            // Create AMQP context for this connection
            this.amqpContext = new AmqpCtxImpl();
            amqpContext.setRemoteHost(host);
            amqpContext.setRemotePort(port);
            ctx.setAttribute("amqp.context", amqpContext);

            // Create codec + channel handler
            var codec = new AmqpFrameCodecImpl((ch, frameData) -> {
                // Complete frame extracted — fire to service
                processInbound(frameData);
            });
            var handler = new AmqpClientChannelHandler(this, codec, amqpContext);

            // Register the channel with the manager and set up pipeline
            var pipeline = channelManager.getChannelPipeline(this);
            pipeline.addFirst(codec);
            pipeline.addLast(handler);

            // Open socket and wrap as data channel
            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(false);
            var dataChannel = new ssg.legoflow.service.channel.TcpDataChannel(sc);
            dataChannel.connect(host, port);
            channelManager.registerChannel(this, dataChannel);

            amqpContext.setState(ProcessorState.READY);
            transitionTo(ProcessorState.READY);
        } catch (Exception e) {
            throw new RuntimeException("AMQP client failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (channelManager != null) channelManager.unregisterChannel(this);
        if (amqpContext != null) amqpContext.setState(ProcessorState.STOPPED);
        transitionTo(ProcessorState.STOPPED);
    }

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

    public AmqpContext getAmqpContext() { return amqpContext; }

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
