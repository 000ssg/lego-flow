package ssg.legoflow.database.postgresql.server;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for PostgreSQL server service. */
public final class PgServerChannelHandler implements ChannelHandler {
    private final PgServerService pgService;

    public PgServerChannelHandler(PgServerService pgService) { this.pgService = pgService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { pgService.consume(pgService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (pgService.isConnected()) pgService.disconnect(pgService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = pgService.getServiceContext();
        if (ctx != null) ctx.setAttribute("pg.server.error", cause);
    }

    public PgServerService getPgService() { return pgService; }
}
