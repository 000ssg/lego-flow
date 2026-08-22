package ssg.legoflow.network.dns.client;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for DNS client service, routing data between DataChannel and DNS transport. */
public final class DnsClientChannelHandler implements ChannelHandler {

    private final DnsClientService dnsClientService;

    public DnsClientChannelHandler(DnsClientService dnsClientService) { 
        this.dnsClientService = dnsClientService; 
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { dnsClientService.consume(dnsClientService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (dnsClientService.isConnected()) dnsClientService.disconnect(dnsClientService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = dnsClientService.getServiceContext();
        if (ctx != null) ctx.setAttribute("dns-client.error", cause);
    }

    public DnsClientService getDnsClientService() { return dnsClientService; }
}
