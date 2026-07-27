package ssg.legoflow.service.channel;

import java.nio.ByteBuffer;

public interface ChannelHandler {

    void onRead(DataChannel channel, ByteBuffer data);

    void onWrite(DataChannel channel);

    void onConnect(DataChannel channel);

    void onDisconnect(DataChannel channel);

    void onError(DataChannel channel, Throwable cause);
}
