package ssg.legoflow.database.postgresql.server;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/** Service-based PostgreSQL server adapter for DP/DF composition. */
public final class PgServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final int port;
    private volatile PgServer server;
    private volatile Consumer<PgResult> queryCallback;

    /** Result of a PostgreSQL query operation. */
    public record PgResult(boolean success, String database, ByteBuffer payload) {
        public static PgResult ok(String db, ByteBuffer data) { return new PgResult(true, db, data); }
        public static PgResult error(String msg) { return new PgResult(false, null, null); }
    }

    PgServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "PostgreSQL Server Service",
                        builder.priority, builder.dependencies));
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.server = new PgServer();
            server.start(port);
        } catch (Exception e) {
            throw new RuntimeException("PostgreSQL server failed to start on port " + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (server != null) { try { server.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public PgServer getServer() { return server; }
    public void setQueryCallback(Consumer<PgResult> cb) { this.queryCallback = cb; }

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
        if (queryCallback != null) queryCallback.accept(PgResult.ok("pg", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new PgServerChannelHandler(this); }

    /** Builder for PostgreSQL server service. */
    public static class Builder {
        private final int port;
        private String name = "pg-server";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(int port) { this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public PgServerService build() { return new PgServerService(this); }
    }

    /** Create a builder for the given port. */
    public static Builder builder(int port) { return new Builder(port); }
}
