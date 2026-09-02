package ssg.legoflow.network.ldap.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for LDAP client service, routing data between DataChannel and LDAP transport. */
public final class LdapClientChannelHandler implements ChannelHandler {

    private final LdapClientService ldapClientService;

    public LdapClientChannelHandler(LdapClientService ldapClientService) { 
        this.ldapClientService = ldapClientService; 
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { ldapClientService.consume(ldapClientService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (ldapClientService.isConnected()) ldapClientService.disconnect(ldapClientService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = ldapClientService.getServiceContext();
        if (ctx != null) ctx.setAttribute("ldap-client.error", cause);
    }

    public LdapClientService getLdapClientService() { return ldapClientService; }
}
