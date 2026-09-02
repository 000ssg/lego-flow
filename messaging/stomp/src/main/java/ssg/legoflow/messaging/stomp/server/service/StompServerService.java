package ssg.legoflow.messaging.stomp.server.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based STOMP server adapter for composition within the service framework. */
public final class StompServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final int port;
    private volatile ssg.legoflow.messaging.stomp.adapter.tcp.TcpStompServer server;
    private volatile Consumer<StompResult> messageCallback;

    public record StompResult(boolean success, String destination, ByteBuffer payload) {
        public static StompResult ok(String dest, ByteBuffer data) { return new StompResult(true, dest, data); }
        public static StompResult error(String msg) { return new StompResult(false, null, null); }
    }

    StompServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "STOMP Server Service", builder.priority, builder.dependencies));
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            var broker = new ssg.legoflow.messaging.stomp.core.StompBroker();
            this.server = new ssg.legoflow.messaging.stomp.adapter.tcp.TcpStompServer(broker, port);
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("STOMP server service failed to start on port " + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (server != null) { try { server.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.messaging.stomp.adapter.tcp.TcpStompServer getServer() { return server; }
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
        if (messageCallback != null) messageCallback.accept(StompResult.ok("server", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new StompServerChannelHandler(this); }

    public static class Builder {
        private final int port;
        private String name = "stomp-server";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(int port) { this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public StompServerService build() { return new StompServerService(this); }
    }

    public static Builder builder(int port) { return new Builder(port); }
}
