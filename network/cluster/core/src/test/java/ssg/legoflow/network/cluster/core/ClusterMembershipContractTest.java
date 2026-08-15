package ssg.legoflow.network.cluster.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abstract contract test for ClusterMembership implementations.
 *
 * Each protocol implementation (DNS-SD, etcd, gRPC) should extend this
 * class and provide a concrete membership instance via {@link #createMembership()}.
 */
abstract class ClusterMembershipContractTest {

    abstract ClusterMembership createMembership();

    /**
     * Returns a node that should NOT be in the cluster (used for leave tests).
     */
    ClusterNode remoteNode() {
        return ClusterNode.builder()
                .id("remote-node")
                .host("127.0.0.1")
                .port(0)
                .build();
    }

    @Test
    void localNodeIsNonNull() {
        var membership = createMembership();
        try {
            assertThat(membership.localNode()).isNotNull();
            assertThat(membership.localNode().id()).isNotEmpty();
        } finally {
            membership.close();
        }
    }

    @Test
    void statusReflectsLocalNode() {
        var membership = createMembership();
        try {
            var status = membership.status();
            assertThat(status.memberCount()).isGreaterThanOrEqualTo(1);
        } finally {
            membership.close();
        }
    }

    @Test
    void listenerReceivesEvents() throws InterruptedException {
        var membership = createMembership();
        try {
            var events = new ArrayList<ClusterEvent>();
            membership.addListener(events::add);

            // Fire a test event
            var testNode = ClusterNode.builder().id("test-node").build();
            membership.fireEvent(new ClusterEvent.NodeJoined(testNode, Instant.now()));

            // Allow async delivery
            waitForEvents(events, 1, 2000);

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(ClusterEvent.NodeJoined.class);
        } finally {
            membership.close();
        }
    }

    @Test
    void removingListenerStopsDelivery() throws InterruptedException {
        var membership = createMembership();
        try {
            var events = new ArrayList<ClusterEvent>();
            ClusterEventListener listener = events::add;

            membership.addListener(listener);
            membership.removeListener(listener);

            var testNode = ClusterNode.builder().id("no-delivery").build();
            membership.fireEvent(new ClusterEvent.NodeJoined(testNode, Instant.now()));

            waitForEvents(events, 0, 1000);
            assertThat(events).isEmpty();
        } finally {
            membership.close();
        }
    }

    @Test
    void multipleListenersAllReceive() throws InterruptedException {
        var membership = createMembership();
        try {
            var events1 = new ArrayList<ClusterEvent>();
            var events2 = new ArrayList<ClusterEvent>();

            membership.addListener(events1::add);
            membership.addListener(events2::add);

            var testNode = ClusterNode.builder().id("multi-listener").build();
            membership.fireEvent(new ClusterEvent.NodeJoined(testNode, Instant.now()));

            waitForEvents(events1, 1, 2000);
            waitForEvents(events2, 1, 2000);

            assertThat(events1).hasSize(1);
            assertThat(events2).hasSize(1);
        } finally {
            membership.close();
        }
    }

    @Test
    void leaveTriggersNodeLeftEvent() throws InterruptedException {
        var membership = createMembership();
        try {
            var leftEvents = new ArrayList<ClusterEvent>();
            membership.addListener(ClusterEventListener.onLeave(node -> {
                leftEvents.add(new ClusterEvent.NodeLeft(node, Instant.now()));
            }));

            membership.leave();
            waitForEvents(leftEvents, 1, 3000);

            assertThat(leftEvents).hasSize(1);
        } finally {
            membership.close();
        }
    }

    @Test
    void closeIsIdempotent() {
        var membership = createMembership();
        membership.close();
        membership.close(); // should not throw
    }

    @Test
    void listenerCannotBeNull() {
        var membership = createMembership();
        try {
            assertThatThrownBy(() -> membership.addListener(null))
                    .isInstanceOf(NullPointerException.class);
        } finally {
            membership.close();
        }
    }

    @Test
    void allEventTypesDelivered() throws InterruptedException {
        var membership = createMembership();
        try {
            var capturedEvents = new ArrayList<ClusterEvent>();
            membership.addListener(capturedEvents::add);

            var node = ClusterNode.builder().id("evt-node").build();
            var now = Instant.now();

            membership.fireEvent(new ClusterEvent.NodeJoined(node, now));
            membership.fireEvent(new ClusterEvent.NodeLeft(node, now));
            membership.fireEvent(new ClusterEvent.NodeFailed(node, now, "test"));
            membership.fireEvent(new ClusterEvent.NodeRecovered(node, now));

            var oldLeader = ClusterNode.builder().id("old").build();
            var newLeader = ClusterNode.builder().id("new").build();
            membership.fireEvent(new ClusterEvent.LeaderChanged(oldLeader, newLeader, now));

            waitForEvents(capturedEvents, 5, 2000);

            assertThat(capturedEvents).hasSize(5);

            // Verify order
            assertThat(capturedEvents.get(0)).isInstanceOf(ClusterEvent.NodeJoined.class);
            assertThat(capturedEvents.get(1)).isInstanceOf(ClusterEvent.NodeLeft.class);
            assertThat(capturedEvents.get(2)).isInstanceOf(ClusterEvent.NodeFailed.class);
            assertThat(capturedEvents.get(3)).isInstanceOf(ClusterEvent.NodeRecovered.class);
            assertThat(capturedEvents.get(4)).isInstanceOf(ClusterEvent.LeaderChanged.class);
        } finally {
            membership.close();
        }
    }

    private void waitForEvents(List<?> events, int expectedSize, long timeoutMs)
            throws InterruptedException {
        var latch = new CountDownLatch(1);
        var checker = new Thread(() -> {
            try {
                var start = System.currentTimeMillis();
                while (events.size() < expectedSize &&
                       System.currentTimeMillis() - start < timeoutMs) {
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        checker.start();
        latch.await(timeoutMs + 500, TimeUnit.MILLISECONDS);
    }
}
