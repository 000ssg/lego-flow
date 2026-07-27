package ssg.legoflow.service.channel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public interface DataChannel extends AutoCloseable {

    int read(ByteBuffer buffer) throws IOException;

    int write(ByteBuffer buffer) throws IOException;

    boolean isOpen();

    SelectionKey getSelectionKey();

    @Override
    void close() throws IOException;
}
