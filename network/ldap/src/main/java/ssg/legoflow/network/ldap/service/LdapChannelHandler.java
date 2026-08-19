package ssg.legoflow.network.ldap.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for LDAP service. */
public final class LdapChannelHandler implements ChannelHandler {
    private final LdapService ldapService;

    public LdapChannelHandler(LdapService ldapService) { this.ldapService = ldapService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { ldapService.consume(ldapService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (ldapService.isConnected()) ldapService.disconnect(ldapService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = ldapService.getServiceContext();
        if (ctx != null) ctx.setAttribute("ldap.error", cause);
    }

    public LdapService getLdapService() { return ldapService; }
}
