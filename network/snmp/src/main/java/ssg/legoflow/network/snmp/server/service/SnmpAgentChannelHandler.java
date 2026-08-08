package ssg.legoflow.network.snmp.server.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for SNMP agent service, routing data between DataChannel and SNMP transport. */
public final class SnmpAgentChannelHandler implements ChannelHandler {

    private final SnmpAgentService snmpAgentService;

    public SnmpAgentChannelHandler(SnmpAgentService snmpAgentService) { 
        this.snmpAgentService = snmpAgentService; 
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { snmpAgentService.consume(snmpAgentService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (snmpAgentService.isConnected()) snmpAgentService.disconnect(snmpAgentService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = snmpAgentService.getServiceContext();
        if (ctx != null) ctx.setAttribute("snmp-agent.error", cause);
    }

    public SnmpAgentService getSnmpAgentService() { return snmpAgentService; }
}
