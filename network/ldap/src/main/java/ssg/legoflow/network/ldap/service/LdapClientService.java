package ssg.legoflow.network.ldap.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.network.ldap.client.LdapClient;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
/** Service-based LDAP client adapter for composition within the service framework. */
public final class LdapClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    
    private volatile LdapClient client;
    private volatile java.util.function.Consumer<LdapResult> searchCallback;

    /** Result of an LDAP search operation. */
    public record LdapResult(boolean success, int entryCount) {
        public static LdapResult ok(int count) { return new LdapResult(true, count); }
        public static LdapResult error(String msg) { return new LdapResult(false, -1); }
    }

    LdapClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "LDAP Client Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            // LdapClient uses static connect() method instead of constructor + connect()
            client = LdapClient.connect(host, port);
        } catch (IOException e) {
            throw new RuntimeException("LDAP connection failed: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    public LdapClient getClient() { return client; }
    public void setSearchCallback(java.util.function.Consumer<LdapResult> cb) { this.searchCallback = cb; }

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
            if (searchCallback != null) searchCallback.accept(LdapResult.ok(0));
        } catch (Exception e) {
            if (searchCallback != null) searchCallback.accept(LdapResult.error(e.getMessage()));
        }
    }

    private void processOutboundData(ByteBuffer data) {}

    public static class Builder {
        private final String host; private final int port;
        private String name = "ldap-client";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;

        public Builder(String h, int p) { this.host = h; this.port = p; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public LdapClientService build() { return new LdapClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
