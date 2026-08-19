package ssg.legoflow.network.cluster.core;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class ClusterManagerTest {

    @Test
    void nodeJoinFiresNodeJoinedEvent() throws InterruptedException {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("manager-test").build();
        var config = ClusterConfig.defaultsFor("join-test");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        var events = new ArrayList<ClusterEvent>();
        manager.addListener(events::add);

        // Simulate a heartbeat from a remote node (simulates join)
        var remoteNode = ClusterNode.builder()
                .id("remote-1")
                .host("10.0.0.2")
                .port(8081)
                .build();
        manager.processHeartbeat(remoteNode);

        waitForEvents(events, 1, 2000);

        assertThat(events).hasSize(1);
        var event = events.get(0);
        assertThat(event).isInstanceOf(ClusterEvent.NodeJoined.class);
        var joined = (ClusterEvent.NodeJoined) event;
        assertThat(joined.node().id()).isEqualTo("remote-1");

        manager.close();
    }

    @Test
    void nodeHeartbeatAfterFailureFiresRecovery() throws InterruptedException {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("recovery-test").build();
        var config = ClusterConfig.defaultsFor("recovery-test");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        var events = new ArrayList<ClusterEvent>();
        manager.addListener(events::add);

        // First add the node
        var remoteNode = ClusterNode.builder()
                .id("remote-r")
                .host("10.0.0.3")
                .port(8082)
                .status(ClusterNodeStatus.FAILED)
                .build();

        // Add as failed first
        manager.processHeartbeat(ClusterNode.builder()
                .id("remote-r")
                .host("10.0.0.3")
                .port(8082)
                .build());

        waitForEvents(events, 1, 2000);

        // Now set it as failed via simulateFailure
        manager.simulateFailure("remote-r");

        // Now send heartbeat to recover
        var recoveredNode = ClusterNode.builder()
                .id("remote-r")
                .host("10.0.0.3")
                .port(8082)
                .build();
        manager.processHeartbeat(recoveredNode);

        var recoveryEvents = events.stream()
                .filter(e -> e instanceof ClusterEvent.NodeRecovered)
                .toList();

        assertThat(recoveryEvents).hasSize(1);
        assertThat(manager.status().members()).hasSizeGreaterThanOrEqualTo(2);

        manager.close();
    }

    @Test
    void goodbyeFiresNodeLeftEvent() throws InterruptedException {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("goodbye-test").build();
        var config = ClusterConfig.defaultsFor("goodbye-test");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        var events = new ArrayList<ClusterEvent>();
        manager.addListener(events::add);

        // Add a remote node first
        var remoteNode = ClusterNode.builder()
                .id("leaving-node")
                .host("10.0.0.4")
                .port(8083)
                .build();
        manager.processHeartbeat(remoteNode);
        waitForEvents(events, 1, 2000);

        events.clear();

        // Process goodbye
        manager.processGoodbye("leaving-node");

        waitForEvents(events, 1, 2000);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ClusterEvent.NodeLeft.class);
        var left = (ClusterEvent.NodeLeft) events.get(0);
        assertThat(left.node().id()).isEqualTo("leaving-node");

        manager.close();
    }

    @Test
    void leaderChangeFiresLeaderChangedEvent() throws InterruptedException {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("leader-test").build();
        var config = ClusterConfig.defaultsFor("leader-test");

        var manager = new ClusterManager(localNode, config, transport, checker);

        var events = new ArrayList<ClusterEvent>();
        manager.addListener(events::add);

        var oldLeader = ClusterNode.builder().id("old-leader").build();
        var newLeader = ClusterNode.builder().id("new-leader").build();

        manager.setLeader(oldLeader);
        assertThat(manager.getLeader()).isEqualTo(oldLeader);

        events.clear();
        manager.setLeader(newLeader);

        waitForEvents(events, 1, 2000);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ClusterEvent.LeaderChanged.class);
        var changed = (ClusterEvent.LeaderChanged) events.get(0);
        assertThat(changed.previousLeader().id()).isEqualTo("old-leader");
        assertThat(changed.newLeader().id()).isEqualTo("new-leader");
    }

    @Test
    void membersReturnsUnmodifiableView() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("unmodifiable").build();
        var config = ClusterConfig.defaultsFor("unmodifiable");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        var members = manager.getMembers();
        assertThat(members).isNotNull();
        assertThatThrownBy(() -> members.put("hack", ClusterNode.builder().id("hack").build()))
                .isInstanceOf(UnsupportedOperationException.class);

        manager.close();
    }

    @Test
    void listenersReturnsUnmodifiableView() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("listeners-view").build();
        var config = ClusterConfig.defaultsFor("listeners-view");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        var listener = ClusterEventListener.onJoin(n -> {});
        manager.addListener(listener);

        var listeners = manager.getListeners();
        assertThat(listeners).contains(listener);
        assertThatThrownBy(() -> listeners.add(e -> {}))
                .isInstanceOf(UnsupportedOperationException.class);

        manager.close();
    }

    @Test
    void leavingNodeHasLeavingStatus() throws InterruptedException {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("leave-status").build();
        var config = ClusterConfig.defaultsFor("leave-status");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        // Verify local node is ACTIVE before leave
        var statusBefore = manager.status();
        assertThat(statusBefore.memberCount()).isEqualTo(1);

        // Leave fires NodeLeft, which removes the node
        var events = new ArrayList<ClusterEvent>();
        manager.addListener(events::add);

        manager.leave();

        waitForEvents(events, 1, 2000);

        // After leave, local node should be removed from members
        var statusAfter = manager.status();
        assertThat(statusAfter.memberCount()).isZero();

        manager.close();
    }

    private void simulateFailure(ClusterManager manager, String nodeId) {
        manager.simulateFailure(nodeId);
    }

    private void waitForEvents(List<?> events, int expectedSize, long timeoutMs)
            throws InterruptedException {
        var latch = new CountDownLatch(1);
        Thread checker = new Thread(() -> {
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
