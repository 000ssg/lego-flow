package ssg.legoflow.http2.stream;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
public class Http2StreamManager {

    private final Map<Integer, Http2Stream> streams = new ConcurrentHashMap<>();
    private final AtomicInteger nextClientStreamId = new AtomicInteger(1);
    private final AtomicInteger nextServerStreamId = new AtomicInteger(2);
    private final boolean isServer;
    private int maxConcurrentStreams;
    private final int initialWindowSize;

    public Http2StreamManager(boolean isServer, int maxConcurrentStreams, int initialWindowSize) {
        this.isServer = isServer;
        this.maxConcurrentStreams = maxConcurrentStreams;
        this.initialWindowSize = initialWindowSize;
    }

    public Http2Stream createStream() {
        int streamId = isServer
                ? nextServerStreamId.getAndAdd(2)
                : nextClientStreamId.getAndAdd(2);
        return createStream(streamId);
    }

    public Http2Stream createStream(int streamId) {
        if (getActiveStreamCount() >= maxConcurrentStreams) {
            throw new IllegalStateException("Max concurrent streams exceeded: " + maxConcurrentStreams);
        }
        var stream = new Http2Stream(streamId, initialWindowSize);
        streams.put(streamId, stream);
        return stream;
    }

    public Http2Stream getStream(int streamId) {
        return streams.get(streamId);
    }

    public Http2Stream getOrCreateStream(int streamId) {
        return streams.computeIfAbsent(streamId, id -> new Http2Stream(id, initialWindowSize));
    }

    public void closeStream(int streamId) {
        var stream = streams.get(streamId);
        if (stream != null && !stream.isClosed()) {
            stream.transitionTo(Http2StreamState.CLOSED);
        }
    }

    public void removeStream(int streamId) {
        streams.remove(streamId);
    }

    public int getActiveStreamCount() {
        return (int) streams.values().stream()
                .filter(Http2Stream::isOpen)
                .count();
    }

    public Collection<Http2Stream> getAllStreams() {
        return Collections.unmodifiableCollection(streams.values());
    }

    public Collection<Http2Stream> getActiveStreams() {
        return streams.values().stream()
                .filter(Http2Stream::isOpen)
                .toList();
    }

    public int nextClientStreamId() {
        return nextClientStreamId.get();
    }

    public int nextServerStreamId() {
        return nextServerStreamId.get();
    }

    public void setMaxConcurrentStreams(int max) {
        this.maxConcurrentStreams = max;
    }

    public int maxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    public boolean isServer() {
        return isServer;
    }

    public int initialWindowSize() {
        return initialWindowSize;
    }

    /**
     * Sets the priority for a stream (RFC 7540 Section 5.3).
     *
     * <p>If the stream has exclusive dependency, all other children of the parent
     * are re-parented under this stream.
     *
     * @param streamId           the stream to set priority for
     * @param dependencyStreamId the parent stream ID (0 for root)
     * @param weight             the weight (1-256)
     * @param exclusive          whether this is an exclusive dependency
     * @since 0.1.0
     */
    public void setPriority(int streamId, int dependencyStreamId, int weight, boolean exclusive) {
        var stream = getOrCreateStream(streamId);
        if (exclusive) {
            // Move all current children of the parent to be children of this stream
            for (var other : streams.values()) {
                if (other.streamId() != streamId
                        && other.dependencyStreamId() == dependencyStreamId) {
                    other.setPriority(streamId, other.weight(), false);
                }
            }
        }
        stream.setPriority(dependencyStreamId, weight, exclusive);
    }

    /**
     * Returns a list of active streams ordered by priority for scheduling.
     *
     * <p>Uses weight-proportional ordering: streams with higher weights
     * appear earlier. Within the same weight, stream ID ordering is used.
     * Dependency relationships are respected — parent streams are scheduled
     * before their children.
     *
     * @return ordered list of active streams for scheduling
     * @since 0.1.0
     */
    public List<Http2Stream> getScheduleOrder() {
        var active = new ArrayList<>(getActiveStreams());
        if (active.isEmpty()) return List.of();

        // Build dependency groups: root streams first, then children
        var rootStreams = new ArrayList<Http2Stream>();
        var childStreams = new ArrayList<Http2Stream>();
        for (var stream : active) {
            if (stream.dependencyStreamId() == 0 ||
                    streams.get(stream.dependencyStreamId()) == null ||
                    !streams.get(stream.dependencyStreamId()).isOpen()) {
                rootStreams.add(stream);
            } else {
                childStreams.add(stream);
            }
        }

        // Sort by weight descending (higher weight = higher priority), then by stream ID
        Comparator<Http2Stream> byPriority = Comparator
                .comparingInt(Http2Stream::weight).reversed()
                .thenComparingInt(Http2Stream::streamId);

        rootStreams.sort(byPriority);
        childStreams.sort(byPriority);

        var result = new ArrayList<Http2Stream>(active.size());
        result.addAll(rootStreams);
        result.addAll(childStreams);
        return Collections.unmodifiableList(result);
    }

    /**
     * Allocates bandwidth shares for the given streams proportional to their weights.
     *
     * <p>Given a total bandwidth, each stream receives a share proportional to
     * its weight relative to sibling streams sharing the same parent.
     *
     * @param totalBandwidth the total bandwidth to allocate
     * @param among          the streams to allocate among
     * @return a map from stream ID to allocated bandwidth
     * @since 0.1.0
     */
    public Map<Integer, Integer> allocateBandwidth(int totalBandwidth, Collection<Http2Stream> among) {
        if (among.isEmpty()) return Map.of();

        int totalWeight = among.stream().mapToInt(Http2Stream::weight).sum();
        if (totalWeight == 0) totalWeight = 1;

        var result = new java.util.LinkedHashMap<Integer, Integer>();
        int remaining = totalBandwidth;
        var list = new ArrayList<>(among);
        for (int i = 0; i < list.size(); i++) {
            var stream = list.get(i);
            int share;
            if (i == list.size() - 1) {
                share = remaining; // Last stream gets the remainder
            } else {
                share = (int) ((long) totalBandwidth * stream.weight() / totalWeight);
            }
            result.put(stream.streamId(), share);
            remaining -= share;
        }
        return result;
    }
}
