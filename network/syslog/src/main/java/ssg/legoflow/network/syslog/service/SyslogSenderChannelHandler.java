package ssg.legoflow.network.syslog.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for syslog sender service. */
public final class SyslogSenderChannelHandler implements ChannelHandler {
    private final SyslogSenderService syslogService;

    public SyslogSenderChannelHandler(SyslogSenderService syslogService) { this.syslogService = syslogService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { syslogService.consume(syslogService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (syslogService.isConnected()) syslogService.disconnect(syslogService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = syslogService.getServiceContext();
        if (ctx != null) ctx.setAttribute("syslog.sender.error", cause);
    }

    public SyslogSenderService getSyslogService() { return syslogService; }
}
