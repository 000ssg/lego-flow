package ssg.legoflow.service.passthrough;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reusable interceptor that captures all wire traffic flowing through a pass-through
 * connection for protocol debugging and interop analysis.
 * <p>
 * Each captured entry records the direction, timestamp, connection info, and raw bytes.
 * The capture list is thread-safe and can be read while the connection is active.
 * <p>
 * Usage:
 * <pre>{@code
 * var capture = new WireCaptureInterceptor();
 * connection.addInterceptor(capture);
 * // ... traffic flows ...
 * List<WireEntry> entries = capture.getEntries();
 * }</pre>
 *
 * @since 0.1.0
 */
public final class WireCaptureInterceptor implements DataInterceptor {

    public record WireEntry(
            Direction direction,
            long timestampMs,
            EstablishedConnection connection,
            byte[] data
    ) {}

    private final CopyOnWriteArrayList<WireEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public ByteBuffer onLocalToRemote(EstablishedConnection connection, ByteBuffer data) {
        capture(Direction.LOCAL_TO_REMOTE, connection, data);
        return data;
    }

    @Override
    public ByteBuffer onRemoteToLocal(EstablishedConnection connection, ByteBuffer data) {
        capture(Direction.REMOTE_TO_LOCAL, connection, data);
        return data;
    }

    private void capture(Direction direction, EstablishedConnection connection, ByteBuffer data) {
        byte[] snapshot = new byte[data.remaining()];
        data.duplicate().get(snapshot); // duplicate so we don't modify position
        entries.add(new WireEntry(direction, System.currentTimeMillis(), connection, snapshot));
    }

    /** Returns an unmodifiable snapshot of all captured wire entries. */
    public List<WireEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** Returns the number of captured entries. */
    public int size() {
        return entries.size();
    }

    /** Clears all captured entries. */
    public void clear() {
        entries.clear();
    }
}
