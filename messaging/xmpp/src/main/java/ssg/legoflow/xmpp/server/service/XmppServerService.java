package ssg.legoflow.xmpp.server.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based XMPP server adapter for DP/DF composition. */
public final class XmppServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final int port;
    private volatile ssg.legoflow.xmpp.server.XmppServer server;
    private volatile Consumer<XmppResult> stanzaCallback;

    public record XmppResult(boolean success, String stanzaType, ByteBuffer payload) {
        public static XmppResult ok(String type, ByteBuffer data) { return new XmppResult(true, type, data); }
        public static XmppResult error(String msg) { return new XmppResult(false, null, null); }
    }

    XmppServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "XMPP Server Service",
                        builder.priority, builder.dependencies));
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.server = new ssg.legoflow.xmpp.server.XmppServer(port);
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("XMPP server failed to start on port " + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (server != null) { try { server.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.xmpp.server.XmppServer getServer() { return server; }
    public void setStanzaCallback(Consumer<XmppResult> cb) { this.stanzaCallback = cb; }

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
        if (stanzaCallback != null) stanzaCallback.accept(XmppResult.ok("xmpp", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new XmppServerChannelHandler(this); }

    public static class Builder {
        private final int port;
        private String name = "xmpp-server";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(int port) { this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public XmppServerService build() { return new XmppServerService(this); }
    }

    public static Builder builder(int port) { return new Builder(port); }
}
