package ssg.legoflow.media.sip.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based SIP client adapter for DP/DF composition. */
public final class SipClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.media.sip.agent.SipUserAgent userAgent;
    private volatile Consumer<SipResult> responseCallback;

    public record SipResult(boolean success, String method, ByteBuffer payload) {
        public static SipResult ok(String m, ByteBuffer data) { return new SipResult(true, m, data); }
        public static SipResult error(String msg) { return new SipResult(false, null, null); }
    }

    SipClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "SIP Client Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            String aor = "sip:user@" + host;
            String contactUri = "sip:user@localhost:" + port;
            this.userAgent = new ssg.legoflow.media.sip.agent.SipUserAgent(aor, contactUri);
        } catch (Exception e) {
            throw new RuntimeException("SIP client failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (userAgent != null) { try { userAgent.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.media.sip.agent.SipUserAgent getUserAgent() { return userAgent; }
    public void setResponseCallback(Consumer<SipResult> cb) { this.responseCallback = cb; }

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
        if (responseCallback != null) responseCallback.accept(SipResult.ok("sip", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new SipClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "sip-client";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public SipClientService build() { return new SipClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
