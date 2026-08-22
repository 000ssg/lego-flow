package ssg.legoflow.messaging.stomp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based STOMP client adapter for composition within the service framework. */
public final class StompClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.messaging.stomp.adapter.tcp.TcpStompClient client;
    private volatile Consumer<StompResult> messageCallback;

    public record StompResult(boolean success, String destination, ByteBuffer payload) {
        public static StompResult ok(String dest, ByteBuffer data) { return new StompResult(true, dest, data); }
        public static StompResult error(String msg) { return new StompResult(false, null, null); }
    }

    StompClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "STOMP Client Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.client = new ssg.legoflow.messaging.stomp.adapter.tcp.TcpStompClient(host, port);
            client.connect("localhost");
        } catch (Exception e) {
            throw new RuntimeException("STOMP client service failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (client != null) { try { client.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.messaging.stomp.adapter.tcp.TcpStompClient getClient() { return client; }
    public void setMessageCallback(Consumer<StompResult> cb) { this.messageCallback = cb; }

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
        if (messageCallback != null) messageCallback.accept(StompResult.ok("client", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new StompClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "stomp-client";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public StompClientService build() { return new StompClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
