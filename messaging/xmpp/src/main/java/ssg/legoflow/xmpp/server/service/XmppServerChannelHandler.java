package ssg.legoflow.xmpp.server.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for XMPP server service. */
public final class XmppServerChannelHandler implements ChannelHandler {
    private final XmppServerService xmppService;

    public XmppServerChannelHandler(XmppServerService xmppService) { this.xmppService = xmppService; }

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
        if (ctx != null) ctx.setAttribute("xmpp.server.error", cause);
    }

    public XmppServerService getXmppService() { return xmppService; }
}
