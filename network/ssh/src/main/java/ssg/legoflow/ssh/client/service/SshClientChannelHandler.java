package ssg.legoflow.ssh.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for SSH client service. */
public final class SshClientChannelHandler implements ChannelHandler {
    private final SshClientService sshService;

    public SshClientChannelHandler(SshClientService sshService) { this.sshService = sshService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { sshService.consume(sshService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (sshService.isConnected()) sshService.disconnect(sshService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = sshService.getServiceContext();
        if (ctx != null) ctx.setAttribute("ssh.client.error", cause);
    }

    public SshClientService getSshService() { return sshService; }
}
