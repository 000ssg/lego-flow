package ssg.legoflow.database.mysql.server;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for MySQL server service. */
public final class MysqlServerChannelHandler implements ChannelHandler {
    private final MysqlServerService mysqlService;

    public MysqlServerChannelHandler(MysqlServerService mysqlService) { this.mysqlService = mysqlService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { mysqlService.consume(mysqlService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (mysqlService.isConnected()) mysqlService.disconnect(mysqlService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = mysqlService.getServiceContext();
        if (ctx != null) ctx.setAttribute("mysql.server.error", cause);
    }

    public MysqlServerService getMysqlService() { return mysqlService; }
}
