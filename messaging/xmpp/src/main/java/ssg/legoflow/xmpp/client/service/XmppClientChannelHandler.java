package ssg.legoflow.xmpp.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for XMPP client service. */
public final class XmppClientChannelHandler implements ChannelHandler {
    private final XmppClientService xmppService;

    public XmppClientChannelHandler(XmppClientService xmppService) { this.xmppService = xmppService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { xmppService.consume(xmppService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (xmppService.isConnected()) xmppService.disconnect(xmppService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = xmppService.getServiceContext();
        if (ctx != null) ctx.setAttribute("xmpp.client.error", cause);
    }

    public XmppClientService getXmppService() { return xmppService; }
}
