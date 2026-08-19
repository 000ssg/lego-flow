package ssg.legoflow.messaging.nats.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for NATS server service, routing data between DataChannel and NATS transport. */
public final class NatsServerChannelHandler implements ChannelHandler {

    private final NatsServerService natsServerService;

    public NatsServerChannelHandler(NatsServerService natsServerService) { 
        this.natsServerService = natsServerService; 
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { natsServerService.consume(natsServerService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (natsServerService.isConnected()) natsServerService.disconnect(natsServerService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = natsServerService.getServiceContext();
        if (ctx != null) ctx.setAttribute("nats-server.error", cause);
    }

    public NatsServerService getNatsServerService() { return natsServerService; }
}
