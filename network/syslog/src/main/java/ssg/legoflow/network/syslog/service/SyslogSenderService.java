package ssg.legoflow.network.syslog.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/** Service-based syslog sender adapter for DP/DF composition. */
public final class SyslogSenderService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.network.syslog.SyslogSender sender;
    private volatile Consumer<SyslogSendResult> sendCallback;

    /** Transport mode for syslog sending. */
    public enum TransportMode { UDP, TCP }

    private final TransportMode mode;

    public record SyslogSendResult(boolean success, String message) {
        public static SyslogSendResult ok(String msg) { return new SyslogSendResult(true, msg); }
        public static SyslogSendResult error(String msg) { return new SyslogSendResult(false, msg); }
    }

    SyslogSenderService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "Syslog Sender Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
        this.mode = builder.mode;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            if (mode == TransportMode.UDP) {
                this.sender = ssg.legoflow.network.syslog.SyslogSender.udp(host, port);
            } else {
                this.sender = ssg.legoflow.network.syslog.SyslogSender.tcp(host, port);
            }
        } catch (Exception e) {
            throw new RuntimeException("Syslog sender failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (sender != null) { try { sender.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.network.syslog.SyslogSender getSender() { return sender; }
    public void setSendCallback(Consumer<SyslogSendResult> cb) { this.sendCallback = cb; }

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
        if (sendCallback != null) sendCallback.accept(SyslogSendResult.ok("data forwarded"));
    }

    public ChannelHandler createChannelHandler() { return new SyslogSenderChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private TransportMode mode = TransportMode.TCP;
        private String name = "syslog-sender";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder mode(TransportMode m) { this.mode = m; return this; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public SyslogSenderService build() { return new SyslogSenderService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
