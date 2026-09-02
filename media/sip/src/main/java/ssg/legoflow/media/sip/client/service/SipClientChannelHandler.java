package ssg.legoflow.media.sip.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for SIP client service. */
public final class SipClientChannelHandler implements ChannelHandler {
    private final SipClientService sipService;

    public SipClientChannelHandler(SipClientService sipService) { this.sipService = sipService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { sipService.consume(sipService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (sipService.isConnected()) sipService.disconnect(sipService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = sipService.getServiceContext();
        if (ctx != null) ctx.setAttribute("sip.client.error", cause);
    }

    public SipClientService getSipService() { return sipService; }
}
