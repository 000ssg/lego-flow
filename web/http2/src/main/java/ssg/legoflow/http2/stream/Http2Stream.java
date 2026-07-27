package ssg.legoflow.http2.stream;

import ssg.legoflow.http.core.HttpHeaders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Http2Stream {

    /** Default stream weight per RFC 7540 Section 5.3.5 (1-256, default 16). */
    public static final int DEFAULT_WEIGHT = 16;

    private final int streamId;
    private Http2StreamState state;
    private int sendWindowSize;
    private int receiveWindowSize;
    private final HttpHeaders headers;
    private final List<ByteBuffer> dataBuffers;

    // Priority tree fields (RFC 7540 Section 5.3)
    private int dependencyStreamId = 0;
    private int weight = DEFAULT_WEIGHT;
    private boolean exclusive = false;

    public Http2Stream(int streamId, int initialWindowSize) {
        this.streamId = streamId;
        this.state = Http2StreamState.IDLE;
        this.sendWindowSize = initialWindowSize;
        this.receiveWindowSize = initialWindowSize;
        this.headers = new HttpHeaders();
        this.dataBuffers = new ArrayList<>();
    }

    public int streamId() {
        return streamId;
    }

    public Http2StreamState state() {
        return state;
    }

    public void transitionTo(Http2StreamState newState) {
        if (!state.canTransitionTo(newState)) {
            throw new IllegalStateException(
                    "Cannot transition stream " + streamId + " from " + state + " to " + newState);
        }
        this.state = newState;
    }

    public int sendWindowSize() {
        return sendWindowSize;
    }

    public int receiveWindowSize() {
        return receiveWindowSize;
    }

    public void adjustSendWindow(int delta) {
        this.sendWindowSize += delta;
    }

    public void adjustReceiveWindow(int delta) {
        this.receiveWindowSize += delta;
    }

    public boolean consumeSendWindow(int size) {
        if (size > sendWindowSize) return false;
        sendWindowSize -= size;
        return true;
    }

    public void consumeReceiveWindow(int size) {
        receiveWindowSize -= size;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public void addData(ByteBuffer data) {
        dataBuffers.add(data.duplicate());
    }

    public ByteBuffer getAccumulatedData() {
        if (dataBuffers.isEmpty()) return ByteBuffer.allocate(0);
        int total = dataBuffers.stream().mapToInt(ByteBuffer::remaining).sum();
        var combined = ByteBuffer.allocate(total);
        for (var buf : dataBuffers) {
            combined.put(buf.duplicate());
        }
        combined.flip();
        return combined;
    }

    public void clearData() {
        dataBuffers.clear();
    }

    public boolean isOpen() {
        return state == Http2StreamState.OPEN
            || state == Http2StreamState.HALF_CLOSED_LOCAL
            || state == Http2StreamState.HALF_CLOSED_REMOTE;
    }

    public boolean isClosed() {
        return state == Http2StreamState.CLOSED;
    }

    public boolean isClientInitiated() {
        return (streamId & 1) == 1;
    }

    public boolean isServerInitiated() {
        return (streamId & 1) == 0 && streamId != 0;
    }

    /**
     * Returns the stream dependency (parent stream ID in the priority tree).
     *
     * @return the dependency stream ID, 0 for root
     * @since 1.0.0
     */
    public int dependencyStreamId() {
        return dependencyStreamId;
    }

    /**
     * Returns the priority weight (1-256).
     *
     * @return the weight
     * @since 1.0.0
     */
    public int weight() {
        return weight;
    }

    /**
     * Returns whether this stream has exclusive dependency.
     *
     * @return true if exclusive
     * @since 1.0.0
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Sets the priority for this stream per RFC 7540 Section 5.3.
     *
     * @param dependencyStreamId the parent stream ID (0 for root)
     * @param weight             the weight (1-256)
     * @param exclusive          whether this is an exclusive dependency
     * @since 1.0.0
     */
    public void setPriority(int dependencyStreamId, int weight, boolean exclusive) {
        this.dependencyStreamId = dependencyStreamId;
        this.weight = Math.max(1, Math.min(256, weight));
        this.exclusive = exclusive;
    }
}
