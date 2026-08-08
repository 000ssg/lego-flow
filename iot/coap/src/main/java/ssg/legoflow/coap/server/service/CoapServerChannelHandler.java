package ssg.legoflow.coap.server.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for CoAP server service. */
public final class CoapServerChannelHandler implements ChannelHandler {
    private final CoapServerService coapService;

    public CoapServerChannelHandler(CoapServerService coapService) { this.coapService = coapService; }

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
        if (ctx != null) ctx.setAttribute("coap.server.error", cause);
    }

    public CoapServerService getCoapService() { return coapService; }
}
