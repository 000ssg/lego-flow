package ssg.legoflow.xmpp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/** Service-based XMPP client adapter for DP/DF composition. */
public final class XmppClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.xmpp.client.XmppClient client;
    private volatile Consumer<XmppResult> stanzaCallback;

    public record XmppResult(boolean success, String stanzaType, ByteBuffer payload) {
        public static XmppResult ok(String type, ByteBuffer data) { return new XmppResult(true, type, data); }
        public static XmppResult error(String msg) { return new XmppResult(false, null, null); }
    }

    XmppClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "XMPP Client Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.client = new ssg.legoflow.xmpp.client.XmppClient();
            var config = ssg.legoflow.xmpp.client.XmppClientConfig.builder(host, host).build();
            client.connect(config).join();
        } catch (Exception e) {
            throw new RuntimeException("XMPP client failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (client != null) { try { client.disconnect(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.xmpp.client.XmppClient getClient() { return client; }
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
        if (stanzaCallback != null) stanzaCallback.accept(XmppResult.ok("stanza", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new XmppClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "xmpp-client";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public XmppClientService build() { return new XmppClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
