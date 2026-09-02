package ssg.legoflow.database.redis.channel;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
/**
 * Channel handler for processing Redis protocol messages.
 *
 * @since 0.1.0
 */
public class RedisChannelHandler implements ChannelHandler {

    @Override
    public void onRead(DataChannel channel, ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) return;
        // Decode the received Redis response
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        // Forward to the next handler in the pipeline
        try { channel.write(ByteBuffer.wrap(data)); }
        catch (IOException e) { onError(channel, e); }
    }

    @Override
    public void onWrite(DataChannel channel) {
        // Channel has data available for writing
    }

    @Override
    public void onConnect(DataChannel channel) {
        // Handle channel activation
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        // Handle channel deactivation
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        // Handle exceptions
        cause.printStackTrace();
    }
}
