package ssg.legoflow.network.ldap.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
/**
 * Service-based LDAP adapter for composition within the service framework.
 */
public final class LdapService extends AbstractService<ByteBuffer, ByteBuffer> {

    public enum Mode { CLIENT, SERVER }

    private final InetSocketAddress bindAddress;
    private final Mode mode;
    
    private volatile ssg.legoflow.network.ldap.server.LdapServer server;
    private volatile java.util.function.Consumer<ByteBuffer> dataCallback;

    LdapService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "LDAP Protocol Service", builder.priority, builder.dependencies));
        this.bindAddress = builder.bindAddress;
        this.mode = builder.mode;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            if (mode == Mode.SERVER) {
                var addr = bindAddress != null ? bindAddress : new InetSocketAddress("0.0.0.0", 389);
                var backend = new ssg.legoflow.network.ldap.server.InMemoryDirectoryBackend();
                this.server = ssg.legoflow.network.ldap.server.LdapServer.start(addr.getPort(), backend);
            }
        } catch (Exception e) {
            throw new RuntimeException("LDAP service failed to connect: " + bindAddress, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {}
        finally {
            transitionTo(ProcessorState.STOPPED);
        }
    }

    public ssg.legoflow.network.ldap.server.LdapServer getServer() { return server; }
    public void setDataCallback(java.util.function.Consumer<ByteBuffer> cb) { this.dataCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        for (ByteBuffer buf : output) {
            try { if (buf != null && buf.hasRemaining()) processOutbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    private void processInbound(ByteBuffer data) {
        if (dataCallback != null) dataCallback.accept(data.asReadOnlyBuffer());
    }
    private void processOutbound(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new LdapChannelHandler(this); }

    public static class Builder {
        private InetSocketAddress bindAddress;
        private String name = "ldap";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private Mode mode = Mode.CLIENT;

        public Builder(String host, int port) { this.bindAddress = new InetSocketAddress(host, port); }
        public Builder name(String n) { this.name = n; return this; }
        public Builder mode(Mode m) { this.mode = m; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public LdapService build() { return new LdapService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
