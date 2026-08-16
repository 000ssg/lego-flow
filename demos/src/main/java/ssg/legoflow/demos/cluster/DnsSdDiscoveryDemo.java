package ssg.legoflow.demos.cluster;

import ssg.legoflow.network.cluster.core.ClusterEvent;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterStatus;
import ssg.legoflow.network.cluster.core.ClusterHealthChecker;
import ssg.legoflow.network.cluster.dns.DnsSdBrowser;
import ssg.legoflow.network.cluster.dns.DnsSdConfig;
import ssg.legoflow.network.cluster.dns.DnsSdDiscovery;
import ssg.legoflow.network.cluster.dns.DnsSdServiceRecord;
import ssg.legoflow.network.cluster.dns.MdnsResponder;
import ssg.legoflow.network.cluster.dns.MdnsQuerier;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Demo: DNS-SD / mDNS Service Discovery for cluster membership.
 *
 * <p>Simulates 3 nodes joining a cluster via mDNS:
 * <ol>
 *   <li>Node A starts, announces itself</li>
 *   <li>Node B starts, probes, announces; A discovers B</li>
 *   <li>Node C starts; all 3 discover each other</li>
 *   <li>Browse services to list all instances</li>
 *   <li>Node B stops (sends goodbye); A and C detect B gone</li>
 * </ol>
 *
 * <p>Note: This demo uses loopback multicast which may not work in all environments.
 * On systems where multicast loopback is not supported, only local discovery occurs.
 */
public final class DnsSdDiscoveryDemo {

    private DnsSdDiscoveryDemo() {}

    private static final InetAddress LOCAL_ADDR;

    static {
        try {
            LOCAL_ADDR = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== DNS-SD / mDNS Discovery Demo ===");
        System.out.println();

        // Step 1: Node A starts
        System.out.println("[1] Starting Node A...");
        DnsSdConfig configA = DnsSdConfig.builder()
                .serviceType("_legoflow._tcp")
                .instanceName("NodeA")
                .port(8001)
                .ttl(Duration.ofSeconds(30))
                .txtAttributes(java.util.Map.of("role", "primary", "region", "local"))
                .bindAddress(LOCAL_ADDR)
                .build();

        List<ClusterEvent> eventsA = new CopyOnWriteArrayList<>();
        try (DnsSdDiscovery discoveryA = new DnsSdDiscovery(configA)) {
            discoveryA.addListener(eventsA::add);
            discoveryA.start();
            Thread.sleep(1500);

            ClusterStatus statusA = discoveryA.status();
            System.out.printf("   Node A: %d member(s), leader=%s%n",
                    statusA.memberCount(),
                    statusA.leader() != null ? statusA.leader().id() : "none");

            // Step 2: Node B starts
            System.out.println("[2] Starting Node B...");
            DnsSdConfig configB = DnsSdConfig.builder()
                    .serviceType("_legoflow._tcp")
                    .instanceName("NodeB")
                    .port(8002)
                    .ttl(Duration.ofSeconds(30))
                    .txtAttributes(java.util.Map.of("role", "replica", "region", "local"))
                    .bindAddress(LOCAL_ADDR)
                    .build();

            List<ClusterEvent> eventsB = new CopyOnWriteArrayList<>();
            try (DnsSdDiscovery discoveryB = new DnsSdDiscovery(configB)) {
                discoveryB.addListener(eventsB::add);
                discoveryB.start();
                Thread.sleep(2000);

                statusA = discoveryA.status();
                ClusterStatus statusB = discoveryB.status();
                System.out.printf("   Node A: %d member(s)%n", statusA.memberCount());
                System.out.printf("   Node B: %d member(s)%n", statusB.memberCount());

                // Step 3: Node C starts
                System.out.println("[3] Starting Node C...");
                DnsSdConfig configC = DnsSdConfig.builder()
                        .serviceType("_legoflow._tcp")
                        .instanceName("NodeC")
                        .port(8003)
                        .ttl(Duration.ofSeconds(30))
                        .txtAttributes(java.util.Map.of("role", "replica", "region", "us-east"))
                        .bindAddress(LOCAL_ADDR)
                        .build();

                List<ClusterEvent> eventsC = new CopyOnWriteArrayList<>();
                try (DnsSdDiscovery discoveryC = new DnsSdDiscovery(configC)) {
                    discoveryC.addListener(eventsC::add);
                    discoveryC.start();
                    Thread.sleep(2000);

                    statusA = discoveryA.status();
                    statusB = discoveryB.status();
                    ClusterStatus statusC = discoveryC.status();
                    System.out.printf("   Node A: %d member(s)%n", statusA.memberCount());
                    System.out.printf("   Node B: %d member(s)%n", statusB.memberCount());
                    System.out.printf("   Node C: %d member(s)%n", statusC.memberCount());

                    // Step 4: Browse services
                    System.out.println("[4] Browsing services...");
                    try (DnsSdBrowser browser = new DnsSdBrowser("_legoflow._tcp", "local", LOCAL_ADDR)) {
                        browser.start();
                        Thread.sleep(1000);

                        List<DnsSdServiceRecord> services = browser.services();
                        if (services.isEmpty()) {
                            System.out.println("   (No services discovered — multicast may be restricted)");
                        } else {
                            for (DnsSdServiceRecord svc : services) {
                                System.out.printf("   %-15s port=%-5d attrs=%s%n",
                                        svc.instanceName(), svc.port(), svc.txtAttributes());
                            }
                        }
                    }

                    // Step 5: Node B leaves
                    System.out.println("[5] Node B leaving cluster...");
                    discoveryB.leave();
                    Thread.sleep(1500);

                    statusA = discoveryA.status();
                    statusC = discoveryC.status();
                    System.out.printf("   Node A after B leaves: %d member(s)%n", statusA.memberCount());
                    System.out.printf("   Node C after B leaves: %d member(s)%n", statusC.memberCount());
                }

                // Summary
                System.out.println();
                System.out.println("=== Event Summary ===");
                System.out.printf("Node A events: %d%n", eventsA.size());
                System.out.printf("Node B events: %d%n", eventsB.size());

                // Print event types for Node A
                if (!eventsA.isEmpty()) {
                    List<String> types = eventsA.stream()
                            .map(e -> e.getClass().getSimpleName())
                            .collect(Collectors.toList());
                    System.out.println("Node A event types: " + types);
                }
            }
        }

        System.out.println();
        System.out.println("Demo complete.");
    }
}
