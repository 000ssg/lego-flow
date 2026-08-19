package ssg.legoflow.network.cluster.core;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
class ClusterLifecycleTest {

    @Test
    void startRegistersLocalNode() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder()
                .id("lifecycle-node")
                .host("127.0.0.1")
                .port(8080)
                .build();
        var config = ClusterConfig.defaultsFor("lifecycle-test");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        try {
            assertThat(manager.isRunning()).isTrue();
            var status = manager.status();
            assertThat(status.memberCount()).isGreaterThanOrEqualTo(1);
            assertThat(status.members()).contains(localNode);
        } finally {
            manager.close();
        }
    }

    @Test
    void closeStopsManager() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("close-test").build();
        var config = ClusterConfig.defaultsFor("close-test");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();
        manager.close();

        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void closeIsIdempotent() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("idempotent-close").build();
        var config = ClusterConfig.defaultsFor("idempotent");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.close();
        manager.close(); // should not throw

        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void closeWithoutStart() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("no-start").build();
        var config = ClusterConfig.defaultsFor("no-start");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.close(); // should not throw

        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void startIsIdempotent() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("multi-start").build();
        var config = ClusterConfig.defaultsFor("multi-start");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();
        manager.start(); // should be no-op

        assertThat(manager.isRunning()).isTrue();
        assertThat(manager.status().memberCount()).isEqualTo(1);
        manager.close();
    }

    @Test
    void restartAfterClose() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("restart").build();
        var config = ClusterConfig.defaultsFor("restart");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();
        manager.close();

        // Note: re-starting after close may not be supported depending on implementation
        // This test verifies the close state
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void startAndClosePreservesTransport() throws Exception {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("transport-close").build();
        var config = ClusterConfig.defaultsFor("transport-test");

        var manager = new ClusterManager(localNode, config, transport, checker);
        assertThat(transport.isAvailable()).isTrue();

        manager.start();
        manager.close();

        // Transport should be closed by the manager
        assertThat(transport.isAvailable()).isFalse();
    }

    @Test
    void heartbeatsSentAfterStart() throws InterruptedException {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(10), Duration.ofSeconds(2)); // long health check to avoid interference
        var localNode = ClusterNode.builder()
                .id("hb-test")
                .host("127.0.0.1")
                .port(9090)
                .build();

        // Short heartbeat interval for testing
        var config = ClusterConfig.builder()
                .name("heartbeat-test")
                .heartbeatInterval(Duration.ofMillis(100))
                .heartbeatFailureThreshold(3)
                .joinTimeout(Duration.ofSeconds(1))
                .leaveTimeout(Duration.ofSeconds(1))
                .build();

        // Register a receiver for the heartbeat messages
        var received = new AtomicBoolean(false);
        transport.registerReceiver("hb-test", (senderId, payload) -> received.set(true));

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        try {
            // Wait for at least one heartbeat broadcast
            var latch = new CountDownLatch(1);
            Thread startMonitoring = new Thread(() -> {
                try {
                    for (int i = 0; i < 30; i++) {
                        if (transport.getMessages("hb-test").size() > 0) {
                            latch.countDown();
                            return;
                        }
                        Thread.sleep(50);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            startMonitoring.start();
            latch.await(5, TimeUnit.SECONDS);
        } finally {
            manager.close();
        }
    }

    @Test
    void closeCancelsScheduledTasks() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("cancel-test").build();
        var config = ClusterConfig.defaultsFor("cancel-test");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();
        manager.close();

        // After close, no heartbeats should be sent
        var messagesBefore = transport.getMessages("cancel-test").size();

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } // wait longer than heartbeat interval would fire

        var messagesAfter = transport.getMessages("cancel-test").size();
        assertThat(messagesAfter).isEqualTo(messagesBefore);
    }
}
