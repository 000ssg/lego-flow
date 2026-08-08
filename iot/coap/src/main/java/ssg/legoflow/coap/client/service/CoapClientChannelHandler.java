package ssg.legoflow.coap.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for CoAP client service. */
public final class CoapClientChannelHandler implements ChannelHandler {
    private final CoapClientService coapService;

    public CoapClientChannelHandler(CoapClientService coapService) { this.coapService = coapService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { coapService.consume(coapService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (coapService.isConnected()) coapService.disconnect(coapService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = coapService.getServiceContext();
        if (ctx != null) ctx.setAttribute("coap.client.error", cause);
    }

    public CoapClientService getCoapService() { return coapService; }
}
