package ssg.legoflow.messaging.nats.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for NATS client service, routing data between DataChannel and NATS transport. */
public final class NatsClientChannelHandler implements ChannelHandler {

    private final NatsService natsService;

    public NatsClientChannelHandler(NatsService natsService) { this.natsService = natsService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { natsService.consume(natsService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (natsService.isConnected()) natsService.disconnect(natsService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = natsService.getServiceContext();
        if (ctx != null) ctx.setAttribute("nats-client.error", cause);
    }

    public NatsService getNatsService() { return natsService; }
}
