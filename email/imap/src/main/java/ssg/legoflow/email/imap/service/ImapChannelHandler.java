package ssg.legoflow.email.imap.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for IMAP service. */
public final class ImapChannelHandler implements ChannelHandler {
    private final ImapService imapService;

    public ImapChannelHandler(ImapService imapService) { this.imapService = imapService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { imapService.consume(imapService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (imapService.isConnected()) imapService.disconnect(imapService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = imapService.getServiceContext();
        if (ctx != null) ctx.setAttribute("imap.error", cause);
    }

    public ImapService getImapService() { return imapService; }
}
