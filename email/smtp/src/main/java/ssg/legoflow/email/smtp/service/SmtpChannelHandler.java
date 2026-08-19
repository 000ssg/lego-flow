package ssg.legoflow.email.smtp.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/**
 * Channel handler for SMTP service, routing data between DataChannel and SMTP transport.
 */
public final class SmtpChannelHandler implements ChannelHandler {

    private final SmtpService smtpService;

    public SmtpChannelHandler(SmtpService smtpService) {
        this.smtpService = smtpService;
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { smtpService.consume(smtpService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (smtpService.isConnected()) smtpService.disconnect(smtpService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = smtpService.getServiceContext();
        if (ctx != null) ctx.setAttribute("smtp.error", cause);
    }

    public SmtpService getSmtpService() { return smtpService; }
}
