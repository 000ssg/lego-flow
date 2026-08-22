package ssg.legoflow.ftp.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for FTP client service. */
public final class FtpClientChannelHandler implements ChannelHandler {
    private final FtpClientService ftpService;

    public FtpClientChannelHandler(FtpClientService ftpService) { this.ftpService = ftpService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { ftpService.consume(ftpService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (ftpService.isConnected()) ftpService.disconnect(ftpService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = ftpService.getServiceContext();
        if (ctx != null) ctx.setAttribute("ftp.client.error", cause);
    }

    public FtpClientService getFtpService() { return ftpService; }
}
