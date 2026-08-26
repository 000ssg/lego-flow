package ssg.legoflow.messaging.amqp.client.service;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.amqp.common.AmqpContext;
import ssg.legoflow.messaging.amqp.transport.AmqpFrameCodec;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
import java.util.Objects;
/** Channel handler for AMQP client service. */
public final class AmqpClientChannelHandler implements ChannelHandler {
    private final AmqpClientService service;
    private final AmqpFrameCodec codec;
    private final AmqpContext context;

    public AmqpClientChannelHandler(AmqpClientService service, AmqpFrameCodec codec, AmqpContext ctx) {
        this.service = Objects.requireNonNull(service);
        this.codec = Objects.requireNonNull(codec);
        this.context = Objects.requireNonNull(ctx);
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        // Feed raw bytes to the service's consume path
        try { service.consume(service.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onWrite(DataChannel channel) {
        // Outbound writes go through the service's produce path
    }

    @Override
    public void onConnect(DataChannel channel) {
        // Finish the async TCP connection before proceeding
        if (channel instanceof ssg.legoflow.service.channel.TcpDataChannel tcp) {
            try {
                tcp.finishConnect();
            } catch (Exception e) {
                onError(channel, e);
                return;
            }
        }
        context.setState(ProcessorState.READY);
        context.setChannel(channel);
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        context.setState(ProcessorState.STOPPED);
        try { if (service.isConnected()) service.disconnect(service.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        context.setError(cause);
        var ctx = service.getServiceContext();
        if (ctx != null) ctx.setAttribute("amqp.client.error", cause);
    }

    public AmqpClientService getService() { return service; }
    public AmqpFrameCodec getCodec() { return codec; }
    public AmqpContext getContext() { return context; }
}
