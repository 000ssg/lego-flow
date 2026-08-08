package ssg.legoflow.email.imap.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.email.imap.client.ImapClient;
import ssg.legoflow.email.imap.client.ImapClientConfig;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Service-based IMAP client adapter for composition within the service framework. */
public final class ImapClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    
    private volatile ImapClient client;
    private volatile java.util.function.Consumer<ImapResult> fetchCallback;

    /** Result of an IMAP operation. */
    public record ImapResult(boolean success, int messageCount) {
        public static ImapResult ok(int count) { return new ImapResult(true, count); }
        public static ImapResult error(String msg) { return new ImapResult(false, -1); }
    }

    ImapClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "IMAP Client Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            var configBuilder = ImapClientConfig.builder(host, port);
            
            this.client = new ImapClient(configBuilder.build());
            client.connect();
        } catch (IOException e) {
            throw new RuntimeException("IMAP connection failed: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    public ImapClient getClient() { return client; }
    public void setFetchCallback(java.util.function.Consumer<ImapResult> cb) { this.fetchCallback = cb; }

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
        try {
            client.select("INBOX");
            if (fetchCallback != null) fetchCallback.accept(ImapResult.ok(0));
        } catch (Exception e) {
            if (fetchCallback != null) fetchCallback.accept(ImapResult.error(e.getMessage()));
        }
    }

    private void processOutboundData(ByteBuffer data) {}

    public static class Builder {
        private final String host; private final int port;
        private String name = "imap-client";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;

        public Builder(String h, int p) { this.host = h; this.port = p; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public ImapClientService build() { return new ImapClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
