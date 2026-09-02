package ssg.legoflow.network.dns.client;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.function.Consumer;
/**
 * Service-based DNS client adapter for composition within the service framework.
 * Data flows as ByteBuffer containing DNS query messages (DnsCodec encoded).
 */
public final class DnsClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private final Duration timeout;
    
    private volatile DnsClient client;
    private volatile Consumer<DnsResult> queryCallback;

    /** Result of a DNS query operation. */
    public record DnsResult(boolean success, int recordCount) {
        public static DnsResult ok(int count) { return new DnsResult(true, count); }
        public static DnsResult error(String msg) { return new DnsResult(false, -1); }
    }

    DnsClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "DNS Client Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofSeconds(5);
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            client = new DnsClient(new InetSocketAddress(host, port), timeout);
        } catch (Exception e) {
            throw new RuntimeException("DNS client connection failed: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    public DnsClient getClient() { return client; }
    public void setQueryCallback(Consumer<DnsResult> cb) { this.queryCallback = cb; }

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
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        try {
            var response = client.query(DnsCodec.decode(bytes));
            if (queryCallback != null) queryCallback.accept(DnsResult.ok(response.answers().size()));
        } catch (Exception e) {
            if (queryCallback != null) queryCallback.accept(DnsResult.error(e.getMessage()));
        }
    }

    private void processOutboundData(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new DnsClientChannelHandler(this); }

    public static class Builder {
        private final String host; private final int port;
        private String name = "dns-client";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private Duration timeout;

        public Builder(String h, int p) { this.host = h; this.port = p; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder timeout(Duration t) { this.timeout = t; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public DnsClientService build() { return new DnsClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
