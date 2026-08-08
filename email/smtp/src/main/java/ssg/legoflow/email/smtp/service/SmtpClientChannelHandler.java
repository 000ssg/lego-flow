package ssg.legoflow.email.smtp.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for SMTP client service, routing data between DataChannel and SMTP transport. */
public final class SmtpClientChannelHandler implements ChannelHandler {

    private final SmtpClientService smtpClientService;

    public SmtpClientChannelHandler(SmtpClientService smtpClientService) {
        this.smtpClientService = smtpClientService;
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { 
            smtpClientService.consume(smtpClientService.getServiceContext(), data); 
        } catch (Exception e) { 
            onError(channel, e); 
        }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { 
            if (smtpClientService.isConnected()) {
                smtpClientService.disconnect(smtpClientService.getServiceContext());
            }
        } catch (Exception e) { 
            onError(channel, e); 
        }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = smtpClientService.getServiceContext();
        if (ctx != null) {
            ctx.setAttribute("smtp-client.error", cause);
        }
    }

    public SmtpClientService getSmtpClientService() { 
        return smtpClientService; 
    }
}
