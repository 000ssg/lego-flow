package ssg.legoflow.messaging.stomp.server.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for STOMP server service. */
public final class StompServerChannelHandler implements ChannelHandler {
    private final StompServerService stompService;

    public StompServerChannelHandler(StompServerService stompService) { this.stompService = stompService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { stompService.consume(stompService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (stompService.isConnected()) stompService.disconnect(stompService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = stompService.getServiceContext();
        if (ctx != null) ctx.setAttribute("stomp.server.error", cause);
    }

    public StompServerService getStompService() { return stompService; }
}
