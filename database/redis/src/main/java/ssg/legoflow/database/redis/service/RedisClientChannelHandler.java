package ssg.legoflow.database.redis.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for Redis client service, routing data between DataChannel and Redis transport. */
public final class RedisClientChannelHandler implements ChannelHandler {

    private final RedisClientService redisClientService;

    public RedisClientChannelHandler(RedisClientService redisClientService) { 
        this.redisClientService = redisClientService; 
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { redisClientService.consume(redisClientService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (redisClientService.isConnected()) redisClientService.disconnect(redisClientService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = redisClientService.getServiceContext();
        if (ctx != null) ctx.setAttribute("redis-client.error", cause);
    }

    public RedisClientService getRedisClientService() { return redisClientService; }
}
