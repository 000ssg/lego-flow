package ssg.legoflow.network.syslog.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Service-based Syslog adapter for composition within the service framework.
 * Supports UDP and TCP transports.
 */
public final class SyslogService extends AbstractService<ByteBuffer, ByteBuffer> {

    public enum Transport { UDP, TCP }

    private final String host;
    private final int port;
    private final Transport transport;
    
    private volatile java.util.function.Consumer<ByteBuffer> logCallback;

    SyslogService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "Syslog Protocol Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
        this.transport = builder.transport;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try { transitionTo(ProcessorState.CONNECTING); }
        catch (Exception e) { throw new RuntimeException("Syslog service failed: " + host, e); }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        transitionTo(ProcessorState.STOPPED);
    }

    public void setLogCallback(java.util.function.Consumer<ByteBuffer> cb) { this.logCallback = cb; }

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
        if (logCallback != null) logCallback.accept(data.asReadOnlyBuffer());
    }
    private void processOutbound(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new SyslogChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "syslog";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private Transport transport = Transport.UDP;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder transport(Transport t) { this.transport = t; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public SyslogService build() { return new SyslogService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
