package ssg.legoflow.messaging.amqp.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for AMQP client service. */
public final class AmqpClientChannelHandler implements ChannelHandler {
    private final AmqpClientService amqpService;

    public AmqpClientChannelHandler(AmqpClientService amqpService) { this.amqpService = amqpService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { amqpService.consume(amqpService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (amqpService.isConnected()) amqpService.disconnect(amqpService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = amqpService.getServiceContext();
        if (ctx != null) ctx.setAttribute("amqp.client.error", cause);
    }

    public AmqpClientService getAmqpService() { return amqpService; }
}
