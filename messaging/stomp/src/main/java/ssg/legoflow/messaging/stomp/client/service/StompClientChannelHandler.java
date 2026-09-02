package ssg.legoflow.messaging.stomp.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for STOMP client service. */
public final class StompClientChannelHandler implements ChannelHandler {
    private final StompClientService stompService;

    public StompClientChannelHandler(StompClientService stompService) { this.stompService = stompService; }

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
        if (ctx != null) ctx.setAttribute("stomp.client.error", cause);
    }

    public StompClientService getStompService() { return stompService; }
}
