package ssg.legoflow.database.mysql.server;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based MySQL server adapter for DP/DF composition. */
public final class MysqlServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final int port;
    private volatile MysqlServer server;
    private volatile Consumer<MysqlResult> queryCallback;

    /** Result of a MySQL query operation. */
    public record MysqlResult(boolean success, String database, ByteBuffer payload) {
        public static MysqlResult ok(String db, ByteBuffer data) { return new MysqlResult(true, db, data); }
        public static MysqlResult error(String msg) { return new MysqlResult(false, null, null); }
    }

    MysqlServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "MySQL Server Service",
                        builder.priority, builder.dependencies));
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.server = new MysqlServer(port);
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("MySQL server failed to start on port " + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (server != null) { try { server.stop(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public MysqlServer getServer() { return server; }
    public void setQueryCallback(Consumer<MysqlResult> cb) { this.queryCallback = cb; }

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
        if (queryCallback != null) queryCallback.accept(MysqlResult.ok("mysql", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new MysqlServerChannelHandler(this); }

    /** Builder for MySQL server service. */
    public static class Builder {
        private final int port;
        private String name = "mysql-server";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(int port) { this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public MysqlServerService build() { return new MysqlServerService(this); }
    }

    /** Create a builder for the given port. */
    public static Builder builder(int port) { return new Builder(port); }
}
