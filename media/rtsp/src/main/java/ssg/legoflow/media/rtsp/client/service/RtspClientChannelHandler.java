package ssg.legoflow.media.rtsp.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for RTSP client service. */
public final class RtspClientChannelHandler implements ChannelHandler {
    private final RtspClientService rtspService;

    public RtspClientChannelHandler(RtspClientService rtspService) { this.rtspService = rtspService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { rtspService.consume(rtspService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (rtspService.isConnected()) rtspService.disconnect(rtspService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = rtspService.getServiceContext();
        if (ctx != null) ctx.setAttribute("rtsp.client.error", cause);
    }

    public RtspClientService getRtspService() { return rtspService; }
}
