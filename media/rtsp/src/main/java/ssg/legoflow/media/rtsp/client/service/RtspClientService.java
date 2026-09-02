package ssg.legoflow.media.rtsp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based RTSP client adapter for DP/DF composition. */
public final class RtspClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.media.rtsp.client.RtspClient client;
    private volatile Consumer<RtspResult> responseCallback;

    public record RtspResult(boolean success, String method, ByteBuffer payload) {
        public static RtspResult ok(String m, ByteBuffer data) { return new RtspResult(true, m, data); }
        public static RtspResult error(String msg) { return new RtspResult(false, null, null); }
    }

    RtspClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "RTSP Client Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            String uri = "rtsp://" + host + ":" + port;
            this.client = new ssg.legoflow.media.rtsp.client.RtspClient(URI.create(uri));
        } catch (Exception e) {
            throw new RuntimeException("RTSP client failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (client != null) { try { client.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.media.rtsp.client.RtspClient getClient() { return client; }
    public void setResponseCallback(Consumer<RtspResult> cb) { this.responseCallback = cb; }

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
        if (responseCallback != null) responseCallback.accept(RtspResult.ok("rtsp", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new RtspClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "rtsp-client";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public RtspClientService build() { return new RtspClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
