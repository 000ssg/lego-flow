package ssg.legoflow.service.demo.combined;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.demo.procedural.EchoService;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CopyOnWriteArrayList;
public class ChannelManagerDemo {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelManagerDemo.class);

    private final SelectableChannelManager manager;
    private final ServiceContext context;

    public ChannelManagerDemo() {
        context = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new SelectableChannelManager(context);
    }

    public SelectableChannelManager getManager() {
        return manager;
    }

    public ServiceContext getContext() {
        return context;
    }

    public EchoService createAndRegisterEchoService() {
        var service = new EchoService();
        manager.register(service);
        return service;
    }

    public InMemoryDataChannel createInMemoryChannel() {
        return new InMemoryDataChannel();
    }

    public RecordingHandler createRecordingHandler() {
        return new RecordingHandler();
    }

    public void shutdown() {
        manager.close();
    }

    public static class InMemoryDataChannel implements DataChannel {

        private final ByteBuffer internalBuffer = ByteBuffer.allocate(4096);
        private volatile boolean open = true;

        public void putData(byte[] data) {
            synchronized (internalBuffer) {
                internalBuffer.put(data);
            }
        }

        @Override
        public int read(ByteBuffer buffer) throws IOException {
            if (!open) throw new IOException("Channel closed");
            synchronized (internalBuffer) {
                internalBuffer.flip();
                int remaining = internalBuffer.remaining();
                if (remaining == 0) {
                    internalBuffer.compact();
                    return 0;
                }
                int toRead = Math.min(remaining, buffer.remaining());
                for (int i = 0; i < toRead; i++) {
                    buffer.put(internalBuffer.get());
                }
                internalBuffer.compact();
                return toRead;
            }
        }

        @Override
        public int write(ByteBuffer buffer) throws IOException {
            if (!open) throw new IOException("Channel closed");
            int written = buffer.remaining();
            synchronized (internalBuffer) {
                internalBuffer.put(buffer);
            }
            return written;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public SelectionKey getSelectionKey() {
            return null;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    public static class RecordingHandler implements ChannelHandler {

        private final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<byte[]> readData = new CopyOnWriteArrayList<>();

        @Override
        public void onRead(DataChannel channel, ByteBuffer data) {
            var bytes = new byte[data.remaining()];
            data.get(bytes);
            readData.add(bytes);
            events.add("READ:" + new String(bytes));
        }

        @Override
        public void onWrite(DataChannel channel) {
            events.add("WRITE");
        }

        @Override
        public void onConnect(DataChannel channel) {
            events.add("CONNECT");
        }

        @Override
        public void onDisconnect(DataChannel channel) {
            events.add("DISCONNECT");
        }

        @Override
        public void onError(DataChannel channel, Throwable cause) {
            events.add("ERROR:" + cause.getMessage());
        }

        public CopyOnWriteArrayList<String> getEvents() {
            return events;
        }

        public CopyOnWriteArrayList<byte[]> getReadData() {
            return readData;
        }
    }
}
