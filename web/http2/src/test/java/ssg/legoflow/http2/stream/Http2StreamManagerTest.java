package ssg.legoflow.http2.stream;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class Http2StreamManagerTest {

    @Test
    void testClientStreamIdAllocation() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var s1 = manager.createStream();
        var s2 = manager.createStream();

        assertThat(s1.streamId()).isEqualTo(1);
        assertThat(s2.streamId()).isEqualTo(3);
    }

    @Test
    void testServerStreamIdAllocation() {
        var manager = new Http2StreamManager(true, 100, 65535);
        var s1 = manager.createStream();
        var s2 = manager.createStream();

        assertThat(s1.streamId()).isEqualTo(2);
        assertThat(s2.streamId()).isEqualTo(4);
    }

    @Test
    void testCreateStreamWithId() {
        var manager = new Http2StreamManager(true, 100, 65535);
        var stream = manager.createStream(5);

        assertThat(stream.streamId()).isEqualTo(5);
        assertThat(manager.getStream(5)).isNotNull();
    }

    @Test
    void testMaxConcurrentStreams() {
        var manager = new Http2StreamManager(false, 2, 65535);
        var s1 = manager.createStream();
        s1.transitionTo(Http2StreamState.OPEN);
        var s2 = manager.createStream();
        s2.transitionTo(Http2StreamState.OPEN);

        assertThatThrownBy(manager::createStream)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Max concurrent streams");
    }

    @Test
    void testClosedStreamsDoNotCountTowardsMax() {
        var manager = new Http2StreamManager(false, 2, 65535);
        var s1 = manager.createStream();
        s1.transitionTo(Http2StreamState.OPEN);
        var s2 = manager.createStream();
        s2.transitionTo(Http2StreamState.OPEN);

        s1.transitionTo(Http2StreamState.CLOSED);

        var s3 = manager.createStream();
        assertThat(s3).isNotNull();
    }

    @Test
    void testGetStream() {
        var manager = new Http2StreamManager(false, 100, 65535);
        manager.createStream(1);

        assertThat(manager.getStream(1)).isNotNull();
        assertThat(manager.getStream(999)).isNull();
    }

    @Test
    void testGetOrCreateStream() {
        var manager = new Http2StreamManager(true, 100, 65535);
        var stream = manager.getOrCreateStream(7);

        assertThat(stream).isNotNull();
        assertThat(stream.streamId()).isEqualTo(7);
        assertThat(manager.getOrCreateStream(7)).isSameAs(stream);
    }

    @Test
    void testCloseStream() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var stream = manager.createStream(1);
        stream.transitionTo(Http2StreamState.OPEN);
        manager.closeStream(1);

        assertThat(stream.isClosed()).isTrue();
    }

    @Test
    void testRemoveStream() {
        var manager = new Http2StreamManager(false, 100, 65535);
        manager.createStream(1);
        manager.removeStream(1);

        assertThat(manager.getStream(1)).isNull();
    }

    @Test
    void testGetActiveStreamCount() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var s1 = manager.createStream();
        s1.transitionTo(Http2StreamState.OPEN);
        var s2 = manager.createStream();
        s2.transitionTo(Http2StreamState.OPEN);

        assertThat(manager.getActiveStreamCount()).isEqualTo(2);

        s1.transitionTo(Http2StreamState.CLOSED);
        assertThat(manager.getActiveStreamCount()).isEqualTo(1);
    }

    @Test
    void testGetAllStreams() {
        var manager = new Http2StreamManager(false, 100, 65535);
        manager.createStream();
        manager.createStream();

        assertThat(manager.getAllStreams()).hasSize(2);
    }

    @Test
    void testSetMaxConcurrentStreams() {
        var manager = new Http2StreamManager(false, 100, 65535);
        manager.setMaxConcurrentStreams(50);

        assertThat(manager.maxConcurrentStreams()).isEqualTo(50);
    }

    // ---- Priority scheduling tests ----

    @Test
    void testSetPriority() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var s1 = manager.createStream(1);
        s1.transitionTo(Http2StreamState.OPEN);

        manager.setPriority(1, 0, 128, false);

        assertThat(s1.dependencyStreamId()).isEqualTo(0);
        assertThat(s1.weight()).isEqualTo(128);
        assertThat(s1.isExclusive()).isFalse();
    }

    @Test
    void testSetPriorityExclusive() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var s1 = manager.createStream(1);
        s1.transitionTo(Http2StreamState.OPEN);
        s1.setPriority(0, 32, false);

        var s3 = manager.createStream(3);
        s3.transitionTo(Http2StreamState.OPEN);
        s3.setPriority(0, 64, false);

        // s5 takes exclusive dependency on root — s1 and s3 should become children of s5
        var s5 = manager.createStream(5);
        s5.transitionTo(Http2StreamState.OPEN);
        manager.setPriority(5, 0, 100, true);

        assertThat(s1.dependencyStreamId()).isEqualTo(5);
        assertThat(s3.dependencyStreamId()).isEqualTo(5);
        assertThat(s5.dependencyStreamId()).isEqualTo(0);
    }

    @Test
    void testGetScheduleOrderByWeight() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var s1 = manager.createStream(1);
        s1.transitionTo(Http2StreamState.OPEN);
        s1.setPriority(0, 32, false);

        var s3 = manager.createStream(3);
        s3.transitionTo(Http2StreamState.OPEN);
        s3.setPriority(0, 256, false);

        var s5 = manager.createStream(5);
        s5.transitionTo(Http2StreamState.OPEN);
        s5.setPriority(0, 128, false);

        var order = manager.getScheduleOrder();
        assertThat(order).hasSize(3);
        // s3 (weight 256) first, then s5 (weight 128), then s1 (weight 32)
        assertThat(order.get(0).streamId()).isEqualTo(3);
        assertThat(order.get(1).streamId()).isEqualTo(5);
        assertThat(order.get(2).streamId()).isEqualTo(1);
    }

    @Test
    void testGetScheduleOrderDependencyParentFirst() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var s1 = manager.createStream(1);
        s1.transitionTo(Http2StreamState.OPEN);
        s1.setPriority(0, 100, false);

        var s3 = manager.createStream(3);
        s3.transitionTo(Http2StreamState.OPEN);
        s3.setPriority(1, 200, false); // child of s1 with higher weight

        var order = manager.getScheduleOrder();
        assertThat(order).hasSize(2);
        // Root streams come first, then children
        assertThat(order.get(0).streamId()).isEqualTo(1);
        assertThat(order.get(1).streamId()).isEqualTo(3);
    }

    @Test
    void testAllocateBandwidthProportional() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var s1 = manager.createStream(1);
        s1.transitionTo(Http2StreamState.OPEN);
        s1.setPriority(0, 100, false);

        var s3 = manager.createStream(3);
        s3.transitionTo(Http2StreamState.OPEN);
        s3.setPriority(0, 100, false);

        var allocation = manager.allocateBandwidth(1000, java.util.List.of(s1, s3));
        assertThat(allocation).containsKeys(1, 3);
        // Equal weights, total 1000 — should be split roughly equally
        int total = allocation.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(1000);
    }

    @Test
    void testAllocateBandwidthWeighted() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var s1 = manager.createStream(1);
        s1.transitionTo(Http2StreamState.OPEN);
        s1.setPriority(0, 200, false);

        var s3 = manager.createStream(3);
        s3.transitionTo(Http2StreamState.OPEN);
        s3.setPriority(0, 50, false);

        var allocation = manager.allocateBandwidth(1000, java.util.List.of(s1, s3));
        // s1 weight=200 out of 250 total = 800, s3 gets remainder = 200
        assertThat(allocation.get(1)).isGreaterThan(allocation.get(3));
        int total = allocation.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(1000);
    }

    @Test
    void testDefaultWeight() {
        var manager = new Http2StreamManager(false, 100, 65535);
        var stream = manager.createStream(1);
        assertThat(stream.weight()).isEqualTo(Http2Stream.DEFAULT_WEIGHT);
    }
}
