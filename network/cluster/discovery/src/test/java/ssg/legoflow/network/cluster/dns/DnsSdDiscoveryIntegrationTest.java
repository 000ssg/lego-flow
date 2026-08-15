package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;
import ssg.legoflow.network.cluster.core.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for DNS-SD based cluster membership.
 *
 * <p>Tests use loopback multicast on 127.0.0.1 to simulate a local cluster
 * without requiring actual network hardware.
 */
class DnsSdDiscoveryIntegrationTest {

    private static final InetAddress LOCAL_ADDR;
    private static final Duration SHORT_TTL = Duration.ofSeconds(10);
    private static final Duration SETUP_TIMEOUT = Duration.ofSeconds(10);

    static {
        try {
            LOCAL_ADDR = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void singleNode_joinsAndLeaves() throws Exception {
        DnsSdConfig config = DnsSdConfig.builder()
                .serviceType("_legoflow._tcp")
                .instanceName("NodeA")
                .port(8001)
                .ttl(SHORT_TTL)
                .bindAddress(LOCAL_ADDR)
                .build();

        List<ClusterEvent> events = new CopyOnWriteArrayList<>();

        try (DnsSdDiscovery discovery = new DnsSdDiscovery(config)) {
            discovery.addListener(events::add);
            discovery.start();

            // Wait for startup
            Thread.sleep(1500);

            ClusterNode local = discovery.localNode();
            assertThat(local.id()).isEqualTo("NodeA");

            ClusterStatus status = discovery.status();
            assertThat(status.memberCount()).isGreaterThanOrEqualTo(1);

            discovery.leave();

            ClusterEvent lastEvent = events.stream()
                    .filter(ClusterEvent.NodeLeft.class::isInstance)
                    .reduce((a, b) -> b)
                    .orElse(null);

            assertThat(lastEvent).isNotNull();
        }
    }

    @Disabled("Requires multicast support on the loopback interface")
    @Test
    void twoNodes_discoverEachOther() throws Exception {
        DnsSdConfig configA = DnsSdConfig.builder()
                .serviceType("_legoflow._tcp")
                .instanceName("NodeA")
                .port(8001)
                .ttl(SHORT_TTL)
                .bindAddress(LOCAL_ADDR)
                .build();

        DnsSdConfig configB = DnsSdConfig.builder()
                .serviceType("_legoflow._tcp")
                .instanceName("NodeB")
                .port(8002)
                .ttl(SHORT_TTL)
                .bindAddress(LOCAL_ADDR)
                .build();

        List<ClusterEvent> eventsA = new CopyOnWriteArrayList<>();
        List<ClusterEvent> eventsB = new CopyOnWriteArrayList<>();

        try (DnsSdDiscovery discoveryA = new DnsSdDiscovery(configA);
             DnsSdDiscovery discoveryB = new DnsSdDiscovery(configB)) {

            discoveryA.addListener(eventsA::add);
            discoveryB.addListener(eventsB::add);

            discoveryA.start();
            Thread.sleep(1000);
            discoveryB.start();

            // Give time for discovery
            Thread.sleep(3000);

            // Node A should have discovered Node B
            Set<ClusterNode> membersA = discoveryA.members();
            assertThat(membersA).hasSizeGreaterThan(1);

            ClusterNode nodeB = discoveryB.localNode();
            boolean foundB = membersA.stream()
                    .anyMatch(n -> n.id().equals(nodeB.id()));
            assertThat(foundB).as("Node A should have discovered Node B").isTrue();

            // Node B should have discovered Node A
            Set<ClusterNode> membersB = discoveryB.members();
            ClusterNode nodeA = discoveryA.localNode();
            boolean foundA = membersB.stream()
                    .anyMatch(n -> n.id().equals(nodeA.id()));
            assertThat(foundA).as("Node B should have discovered Node A").isTrue();
        }
    }

    @Disabled("Requires multicast support on the loopback interface")
    @Test
    void threeNodes_fullDiscovery() throws Exception {
        String[] names = {"NodeA", "NodeB", "NodeC"};
        int[] ports = {8011, 8012, 8013};

        List<DnsSdDiscovery> discoveries = new ArrayList<>();
        List<List<ClusterEvent>> allEvents = new ArrayList<>();

        try {
            for (int i = 0; i < 3; i++) {
                DnsSdConfig config = DnsSdConfig.builder()
                        .serviceType("_legoflow._tcp")
                        .instanceName(names[i])
                        .port(ports[i])
                        .ttl(SHORT_TTL)
                        .bindAddress(LOCAL_ADDR)
                        .addTxtAttribute("node_role", "member")
                        .build();

                List<ClusterEvent> events = new CopyOnWriteArrayList<>();
                DnsSdDiscovery discovery = new DnsSdDiscovery(config);
                discovery.addListener(events::add);
                discovery.start();

                discoveries.add(discovery);
                allEvents.add(events);

                Thread.sleep(500); // Stagger starts
            }

            // Give time for full discovery mesh
            Thread.sleep(3000);

            // Each node should know about all others
            for (int i = 0; i < 3; i++) {
                Set<ClusterNode> members = discoveries.get(i).members();
                assertThat(members).as(names[i] + " should know about at least 2 nodes")
                        .hasSizeGreaterThanOrEqualTo(2);
            }
        } finally {
            // Clean up in reverse order
            for (int i = discoveries.size() - 1; i >= 0; i--) {
                discoveries.get(i).leave();
                discoveries.get(i).close();
            }
        }
    }

    @Disabled("Requires multicast support on the loopback interface")
    @Test
    void nodeLeave_detectedByPeers() throws Exception {
        DnsSdConfig configA = DnsSdConfig.builder()
                .serviceType("_legoflow._tcp")
                .instanceName("LeaveA")
                .port(8021)
                .ttl(SHORT_TTL)
                .bindAddress(LOCAL_ADDR)
                .build();

        DnsSdConfig configB = DnsSdConfig.builder()
                .serviceType("_legoflow._tcp")
                .instanceName("LeaveB")
                .port(8022)
                .ttl(SHORT_TTL)
                .bindAddress(LOCAL_ADDR)
                .build();

        List<ClusterEvent> eventsB = new CopyOnWriteArrayList<>();

        try (DnsSdDiscovery discoveryA = new DnsSdDiscovery(configA);
             DnsSdDiscovery discoveryB = new DnsSdDiscovery(configB)) {

            discoveryB.addListener(eventsB::add);

            discoveryA.start();
            Thread.sleep(1000);
            discoveryB.start();
            Thread.sleep(2000);

            // Node B should have discovered Node A
            Set<ClusterNode> membersB = discoveryB.members();
            boolean sawA = membersB.stream()
                    .anyMatch(n -> n.id().equals("LeaveA"));
            assertThat(sawA).isTrue();

            // Now leave Node A
            discoveryA.leave();
            Thread.sleep(2000);

            // Node B should have received a NodeLeft event for A
            boolean gotLeaveEvent = eventsB.stream()
                    .filter(ClusterEvent.NodeLeft.class::isInstance)
                    .anyMatch(e -> {
                        ClusterEvent.NodeLeft nl = (ClusterEvent.NodeLeft) e;
                        return nl.node().id().equals("LeaveA");
                    });
            assertThat(gotLeaveEvent).as("Node B should detect Node A leaving").isTrue();
        }
    }

    @Test
    void status_returnsCorrectMemberCount() throws Exception {
        DnsSdConfig config = DnsSdConfig.builder()
                .serviceType("_legoflow._tcp")
                .instanceName("StatusTest")
                .port(8031)
                .ttl(SHORT_TTL)
                .bindAddress(LOCAL_ADDR)
                .build();

        try (DnsSdDiscovery discovery = new DnsSdDiscovery(config)) {
            discovery.start();
            Thread.sleep(1000);

            ClusterStatus status = discovery.status();
            assertThat(status.memberCount()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void addRemoveListener() throws Exception {
        DnsSdConfig config = DnsSdConfig.builder()
                .serviceType("_legoflow._tcp")
                .instanceName("ListenerTest")
                .port(8041)
                .ttl(SHORT_TTL)
                .bindAddress(LOCAL_ADDR)
                .build();

        AtomicInteger countA = new AtomicInteger(0);
        AtomicInteger countB = new AtomicInteger(0);

        try (DnsSdDiscovery discovery = new DnsSdDiscovery(config)) {
            ClusterEventListener listenerA = e -> countA.incrementAndGet();
            ClusterEventListener listenerB = e -> countB.incrementAndGet();

            discovery.addListener(listenerA);
            discovery.addListener(listenerB);

            discovery.start();
            Thread.sleep(500);

            // Fire a test event
            ClusterEvent testEvent = new ClusterEvent.NodeJoined(
                    discovery.localNode(), Instant.now());
            discovery.fireEvent(testEvent);

            assertThat(countA.get()).isEqualTo(1);
            assertThat(countB.get()).isEqualTo(1);

            // Remove listener A
            discovery.removeListener(listenerA);
            discovery.fireEvent(testEvent);

            assertThat(countA.get()).isEqualTo(1); // Not incremented
            assertThat(countB.get()).isEqualTo(2);
        }
    }
}
