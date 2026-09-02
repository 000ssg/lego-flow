package ssg.legoflow.media.rtsp.server.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for RTSP server service. */
public final class RtspServerChannelHandler implements ChannelHandler {
    private final RtspServerService rtspService;

    public RtspServerChannelHandler(RtspServerService rtspService) { this.rtspService = rtspService; }

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
        if (ctx != null) ctx.setAttribute("rtsp.server.error", cause);
    }

    public RtspServerService getRtspService() { return rtspService; }
}
