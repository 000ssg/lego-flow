package ssg.legoflow.http2.stream;

import ssg.legoflow.http2.frame.Http2Frame;
import java.util.concurrent.atomic.AtomicInteger;
public class Http2FlowControl {

    public static final int DEFAULT_INITIAL_WINDOW_SIZE = 65535;
    public static final int MAX_WINDOW_SIZE = Integer.MAX_VALUE;

    private final AtomicInteger connectionSendWindow;
    private final AtomicInteger connectionReceiveWindow;
    private final int initialWindowSize;

    public Http2FlowControl() {
        this(DEFAULT_INITIAL_WINDOW_SIZE);
    }

    public Http2FlowControl(int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
        this.connectionSendWindow = new AtomicInteger(initialWindowSize);
        this.connectionReceiveWindow = new AtomicInteger(initialWindowSize);
    }

    public int connectionSendWindow() {
        return connectionSendWindow.get();
    }

    public int connectionReceiveWindow() {
        return connectionReceiveWindow.get();
    }

    public int initialWindowSize() {
        return initialWindowSize;
    }

    public boolean consumeConnectionSendWindow(int size) {
        while (true) {
            int current = connectionSendWindow.get();
            if (size > current) return false;
            if (connectionSendWindow.compareAndSet(current, current - size)) return true;
        }
    }

    public void consumeConnectionReceiveWindow(int size) {
        connectionReceiveWindow.addAndGet(-size);
    }

    public void applyConnectionWindowUpdate(int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("WINDOW_UPDATE increment must be positive");
        }
        long newWindow = (long) connectionSendWindow.get() + increment;
        if (newWindow > MAX_WINDOW_SIZE) {
            throw new IllegalStateException("Connection send window overflow");
        }
        connectionSendWindow.addAndGet(increment);
    }

    public void applyStreamWindowUpdate(Http2Stream stream, int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("WINDOW_UPDATE increment must be positive");
        }
        long newWindow = (long) stream.sendWindowSize() + increment;
        if (newWindow > MAX_WINDOW_SIZE) {
            throw new IllegalStateException("Stream send window overflow for stream " + stream.streamId());
        }
        stream.adjustSendWindow(increment);
    }

    public Http2Frame createWindowUpdate(int streamId, int increment) {
        return Http2Frame.windowUpdate(streamId, increment);
    }

    public void processWindowUpdate(Http2Frame frame, Http2Stream stream) {
        var payload = frame.payload();
        int increment = payload.getInt() & 0x7FFFFFFF;
        if (increment == 0) {
            throw new IllegalStateException("WINDOW_UPDATE with zero increment");
        }
        if (frame.streamId() == 0) {
            applyConnectionWindowUpdate(increment);
        } else if (stream != null) {
            applyStreamWindowUpdate(stream, increment);
        }
    }

    public int calculateMaxSendSize(Http2Stream stream, int maxFrameSize) {
        int streamWindow = stream.sendWindowSize();
        int connWindow = connectionSendWindow.get();
        return Math.min(Math.min(streamWindow, connWindow), maxFrameSize);
    }

    public void updateInitialWindowSize(int newInitialWindowSize, Iterable<Http2Stream> streams) {
        int delta = newInitialWindowSize - initialWindowSize;
        for (var stream : streams) {
            if (stream.isOpen()) {
                stream.adjustSendWindow(delta);
            }
        }
    }

    public void restoreConnectionReceiveWindow(int amount) {
        connectionReceiveWindow.addAndGet(amount);
    }
}
