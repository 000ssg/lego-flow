package ssg.legoflow.network.dns.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/**
 * Channel handler for DNS service, routing data between DataChannel and DNS transport layer.
 */
public final class DnsChannelHandler implements ChannelHandler {

    private static final int DEFAULT_BUFFER_SIZE = 512;
    
    private final DnsService dnsService;
    private volatile ByteBuffer inboundBuffer;

    public DnsChannelHandler(DnsService dnsService) {
        this.dnsService = dnsService;
        this.inboundBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try {
            dnsService.consume(dnsService.getServiceContext(), data);
        } catch (Exception e) {
            onError(channel, e);
        }
    }

    @Override
    public void onWrite(DataChannel channel) {
        // DNS is typically request/response - no persistent write state needed
    }

    @Override
    public void onConnect(DataChannel channel) {
        // Channel connected - DNS service manages its own connection lifecycle
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        try {
            if (dnsService.isConnected()) {
                dnsService.disconnect(dnsService.getServiceContext());
            }
        } catch (Exception e) {
            onError(channel, e);
        }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = dnsService.getServiceContext();
        if (ctx != null) {
            ctx.setAttribute("dns.error", cause);
        }
    }

    /** Returns the associated DNS service. */
    public DnsService getDnsService() {
        return dnsService;
    }

    /** Sends data through the DNS connection via this handler. */
    public void sendData(DataChannel channel, ByteBuffer data) {
        if (dnsService.isConnected()) {
            dnsService.submit(dnsService.getServiceContext(), data);
        }
    }
}
