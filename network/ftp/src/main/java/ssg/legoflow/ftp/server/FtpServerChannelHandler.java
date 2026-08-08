package ssg.legoflow.ftp.server;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for FTP server service, routing data between DataChannel and FTP transport. */
public final class FtpServerChannelHandler implements ChannelHandler {

    private final FtpServerService ftpServerService;

    public FtpServerChannelHandler(FtpServerService ftpServerService) { 
        this.ftpServerService = ftpServerService; 
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { ftpServerService.consume(ftpServerService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (ftpServerService.isConnected()) ftpServerService.disconnect(ftpServerService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = ftpServerService.getServiceContext();
        if (ctx != null) ctx.setAttribute("ftp-server.error", cause);
    }

    public FtpServerService getFtpServerService() { return ftpServerService; }
}
