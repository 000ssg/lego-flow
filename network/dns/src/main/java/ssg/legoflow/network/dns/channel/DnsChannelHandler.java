package ssg.legoflow.network.dns.channel;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.network.dns.DnsMessage;
import ssg.legoflow.network.dns.codec.DnsCodec;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Channel handler for processing DNS protocol messages.
 *
 * @since 0.1.0
 */
public class DnsChannelHandler implements ChannelHandler {

    @Override
    public void onRead(DataChannel channel, ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) return;
        // Decode the received DNS message
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        DnsMessage message = DnsCodec.decode(data);
        // Forward to the next handler in the pipeline
        try { channel.write(ByteBuffer.wrap(DnsCodec.encode(message))); }
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
