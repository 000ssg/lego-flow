package ssg.legoflow.ssh.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based SSH client adapter for composition within the service framework. */
public final class SshClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.ssh.client.SshClient client;
    private volatile Consumer<SshSessionResult> sessionCallback;

    public record SshSessionResult(boolean success, String channel, ByteBuffer payload) {
        public static SshSessionResult ok(String ch, ByteBuffer data) { return new SshSessionResult(true, ch, data); }
        public static SshSessionResult error(String msg) { return new SshSessionResult(false, null, null); }
    }

    SshClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "SSH Client Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.client = new ssg.legoflow.ssh.client.SshClient();
            client.connect(host, port);
        } catch (Exception e) {
            throw new RuntimeException("SSH client service failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (client != null) { try { client.disconnect(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.ssh.client.SshClient getClient() { return client; }
    public void setSessionCallback(Consumer<SshSessionResult> cb) { this.sessionCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    private void processInbound(ByteBuffer data) {
        if (sessionCallback != null) sessionCallback.accept(SshSessionResult.ok("default", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new SshClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "ssh-client";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public SshClientService build() { return new SshClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
