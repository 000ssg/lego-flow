package ssg.legoflow.email.smtp.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.email.smtp.server.SmtpServer;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/** Service-based SMTP adapter for composition within the service framework. */
public final class SmtpService extends AbstractService<ByteBuffer, ByteBuffer> {

    public enum Mode { CLIENT, SERVER }

    private final InetSocketAddress bindAddress;
    private final Mode mode;
    
    private volatile SmtpServer server;
    private volatile java.util.function.Consumer<ByteBuffer> mailCallback;

    SmtpService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "SMTP Protocol Service", builder.priority, builder.dependencies));
        this.bindAddress = builder.bindAddress;
        this.mode = builder.mode;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            if (mode == Mode.SERVER) {
                var addr = bindAddress != null ? bindAddress : new InetSocketAddress("0.0.0.0", 25);
                this.server = new SmtpServer(addr.getHostName(), addr.getPort());
                server.start();
            }
        } catch (Exception e) {
            throw new RuntimeException("SMTP service failed to connect: " + bindAddress, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (server != null) server.close();
        transitionTo(ProcessorState.STOPPED);
    }

    public SmtpServer getServer() { return server; }
    public void setMailCallback(java.util.function.Consumer<ByteBuffer> cb) { this.mailCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInboundData(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        for (ByteBuffer buf : output) {
            try { if (buf != null && buf.hasRemaining()) processOutboundData(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    private void processInboundData(ByteBuffer data) {
        if (mailCallback != null) mailCallback.accept(data.asReadOnlyBuffer());
    }
    private void processOutboundData(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new SmtpChannelHandler(this); }

    public static class Builder {
        private InetSocketAddress bindAddress;
        private String name = "smtp";
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
        public SmtpService build() { return new SmtpService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
