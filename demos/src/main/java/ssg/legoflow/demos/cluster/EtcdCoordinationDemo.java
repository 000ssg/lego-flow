package ssg.legoflow.demos.cluster;

import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;
import ssg.legoflow.service.cluster.coordination.*;
import ssg.legoflow.service.cluster.coordination.raft.RaftLeaderElection;
import ssg.legoflow.service.cluster.coordination.raft.RaftLogEntry;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Demo: etcd-backed cluster coordination.
 *
 * <p>Demonstrates the etcd/Raft coordination primitives:
 * <ol>
 *   <li>Distributed key-value store with transactions</li>
 *   <li>Leader election with automatic failover</li>
 *   <li>Distributed lock with contention handling</li>
 *   <li>Lease management with auto keep-alive sessions</li>
 *   <li>Watch-based reactive notifications</li>
 *   <li>Cluster discovery via etcd registrations</li>
 *   <li>Raft log entries for consensus terminology</li>
 * </ol>
 *
 * <p>Simulates a multi-node cluster using an in-memory etcd SPI implementation.
 */
public final class EtcdCoordinationDemo {

    private EtcdCoordinationDemo() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== etcd / Raft Coordination Demo ===");
        System.out.println();

        // Shared etcd client + store (simulates a single etcd cluster)
        EtcdConfig config = EtcdConfig.builder().build();
        try (EtcdClient client = new EtcdClient(config)) {
            EtcdKVStore store = new EtcdKVStore(client);

            demoKeyValueStore(store);
            System.out.println();

            demoTransactions(store);
            System.out.println();

            demoLeaderElection(client, store);
            System.out.println();

            demoDistributedLock(client, store);
            System.out.println();

            demoWatchAndReact(client, store);
            System.out.println();

            demoClusterDiscovery(client, store);
            System.out.println();

            demoRaftLog();
            System.out.println();

            demoSession(client, store);
        }

        System.out.println("=== Demo complete ===");
    }

    // ---- Key-Value Store ----

    private static void demoKeyValueStore(EtcdKVStore store) throws Exception {
        System.out.println("--- KV Store ---");

        store.put("/config/max-retries", "3".getBytes(StandardCharsets.UTF_8)).join();
        store.put("/config/timeout", "5000".getBytes(StandardCharsets.UTF_8)).join();
        store.put("/nodes/node-1", "active".getBytes(StandardCharsets.UTF_8)).join();

        String maxRetries = store.getAsString("/config/max-retries").join();
        System.out.println("  max-retries = " + maxRetries);

        String timeout = store.getAsString("/config/timeout").join();
        System.out.println("  timeout = " + timeout);

        var configEntries = store.range("/config/").join();
        System.out.println("  config entries: " + configEntries.size());
        configEntries.forEach((k, v) ->
                System.out.println("    " + k + " = " + new String(v, StandardCharsets.UTF_8)));

        store.delete("/nodes/node-1").join();
        boolean exists = store.get("/nodes/node-1").join() != null;
        System.out.println("  node-1 still exists: " + exists);
        System.out.println("  store revision: " + store.revision());
    }

    // ---- Transactions (CAS) ----

    private static void demoTransactions(EtcdKVStore store) throws Exception {
        System.out.println("--- Transactions (Compare-and-Swap) ---");

        store.put("/counter", "0".getBytes(StandardCharsets.UTF_8)).join();

        // Successful CAS: current value is "0"
        boolean ok1 = EtcdTransaction.create(store, "/counter", "0".getBytes(StandardCharsets.UTF_8))
                .thenPut("/counter", "1".getBytes(StandardCharsets.UTF_8))
                .execute()
                .join();
        System.out.println("  CAS (0 -> 1): " + (ok1 ? "OK" : "FAILED"));

        // Successful CAS: current value is "1"
        boolean ok2 = EtcdTransaction.create(store, "/counter", "1".getBytes(StandardCharsets.UTF_8))
                .thenPut("/counter", "2".getBytes(StandardCharsets.UTF_8))
                .execute()
                .join();
        System.out.println("  CAS (1 -> 2): " + (ok2 ? "OK" : "FAILED"));

        // Failed CAS: expected "1" but actual is "2"
        boolean ok3 = EtcdTransaction.create(store, "/counter", "1".getBytes(StandardCharsets.UTF_8))
                .thenPut("/counter", "99".getBytes(StandardCharsets.UTF_8))
                .execute()
                .join();
        System.out.println("  CAS (stale 1 -> 99): " + (ok3 ? "OK" : "FAILED (expected)"));

        String value = store.getAsString("/counter").join();
        System.out.println("  final counter = " + value);
    }

    // ---- Leader Election ----

    private static void demoLeaderElection(EtcdClient client, EtcdKVStore store) throws Exception {
        System.out.println("--- Leader Election ---");

        try (EtcdElection e1 = new EtcdElection(client, store, "web-cluster", "node-A");
             EtcdElection e2 = new EtcdElection(client, store, "web-cluster", "node-B");
             EtcdElection e3 = new EtcdElection(client, store, "web-cluster", "node-C")) {

            List<EtcdElection.Leader> leaders = new CopyOnWriteArrayList<>();
            e1.onLeaderChanged(leaders::add);
            e2.onLeaderChanged(leaders::add);
            e3.onLeaderChanged(leaders::add);

            // Node A campaigns first
            boolean wonA = e1.campaign(30).join();
            System.out.println("  Node A campaigns: " + (wonA ? "WON" : "LOST"));

            // Node B tries (should lose since A is leader)
            Thread.sleep(100);
            boolean wonB = e2.campaign(30).join();
            System.out.println("  Node B campaigns: " + (wonB ? "WON" : "LOST (expected)"));

            // Node C tries (should also lose)
            boolean wonC = e3.campaign(30).join();
            System.out.println("  Node C campaigns: " + (wonC ? "WON" : "LOST (expected)"));

            // Observe leader from Node B's perspective
            EtcdElection.Leader leader = e2.observe().join();
            System.out.println("  Current leader (observed by B): " +
                    (leader != null ? leader.nodeId() : "none"));

            // Node A resigns
            e1.resign().join();
            System.out.println("  Node A resigns");

            // Node B campaigns again (should win now)
            Thread.sleep(100);
            boolean wonB2 = e2.campaign(30).join();
            System.out.println("  Node B campaigns again: " + (wonB2 ? "WON" : "LOST"));

            leader = e3.observe().join();
            System.out.println("  Current leader (observed by C): " +
                    (leader != null ? leader.nodeId() : "none"));
            System.out.println("  Election listeners fired: " + leaders.size() + " times");
        }
    }

    // ---- Distributed Lock ----

    private static void demoDistributedLock(EtcdClient client, EtcdKVStore store) throws Exception {
        System.out.println("--- Distributed Lock ---");

        // Sequential lock usage (no contention)
        try (EtcdLease lease1 = EtcdLease.grant(client, 10).join()) {
            try (EtcdLock lock1 = new EtcdLock(store, lease1, "db-connection")) {
                lock1.lock().join();
                System.out.println("  Node 1 acquires lock: " + lock1.isHeld());
                System.out.println("  [Node 1] doing critical work...");
                Thread.sleep(200);
                lock1.unlock().join();
                System.out.println("  Node 1 releases lock");
            }
        }

        // Another node acquires after release
        try (EtcdLease lease2 = EtcdLease.grant(client, 10).join()) {
            try (EtcdLock lock2 = new EtcdLock(store, lease2, "db-connection")) {
                lock2.lock().join();
                System.out.println("  Node 2 acquires lock: " + lock2.isHeld());
                System.out.println("  [Node 2] doing critical work...");
                Thread.sleep(100);
                lock2.unlock().join();
                System.out.println("  Node 2 releases lock");
            }
        }

        System.out.println("  Lock + lease demo complete");
    }

    // ---- Watch & React ----

    private static void demoWatchAndReact(EtcdClient client, EtcdKVStore store) throws Exception {
        System.out.println("--- Watch & React ---");

        List<EtcdWatcher.WatchEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (EtcdWatcher watcher = new EtcdWatcher(store, "/config/")) {
            watcher.onEvent(event -> {
                System.out.println("  Watch event: " + event.type() + " key=" + event.key());
                latch.countDown();
                events.add(event);
            });
            watcher.start();

            // Make changes to watched prefix
            store.put("/config/feature-x", "enabled".getBytes(StandardCharsets.UTF_8)).join();
            System.out.println("  PUT /config/feature-x = enabled");

            Thread.sleep(300);
            latch.await(2, TimeUnit.SECONDS);

            store.put("/config/feature-y", "disabled".getBytes(StandardCharsets.UTF_8)).join();
            System.out.println("  PUT /config/feature-y = disabled");

            Thread.sleep(300);
            System.out.println("  Total events received: " + events.size());
        }
    }

    // ---- Cluster Discovery ----

    private static void demoClusterDiscovery(EtcdClient client, EtcdKVStore store) throws Exception {
        System.out.println("--- Cluster Discovery (via etcd) ---");

        InetAddress local = InetAddress.getByName("127.0.0.1");

        ClusterNode node1 = ClusterNode.builder()
                .id("node-1").host(local).port(8080)
                .role(ClusterRole.SERVER).status(ClusterNodeStatus.ACTIVE)
                .build();

        ClusterNode node2 = ClusterNode.builder()
                .id("node-2").host(local).port(8081)
                .role(ClusterRole.SERVER).status(ClusterNodeStatus.ACTIVE)
                .build();

        ClusterNode node3 = ClusterNode.builder()
                .id("node-3").host(local).port(8082)
                .role(ClusterRole.SERVER).status(ClusterNodeStatus.ACTIVE)
                .build();

        // Node 1 starts discovery (registers itself and watches)
        try (EtcdDiscovery disc1 = new EtcdDiscovery(store, client, "web-cluster", node1)) {
            disc1.start().join();
            System.out.println("  node-1 registered");

            Thread.sleep(200);

            // Get status from node 1
            var status = disc1.status();
            System.out.println("  Cluster status:");
            System.out.println("    members: " + status.memberCount());
            for (var node : status.members()) {
                System.out.println("      " + node.id() + " : " +
                        node.host() + ":" + node.port() + " [" + node.status() + "]");
            }

            // Node 2 joins
            try (EtcdDiscovery disc2 = new EtcdDiscovery(store, client, "web-cluster", node2)) {
                disc2.start().join();
                System.out.println("  node-2 registered");
                Thread.sleep(200);

                status = disc2.status();
                System.out.println("  After node-2 joins: " + status.memberCount() + " members");
                for (var node : status.members()) {
                    System.out.println("    " + node.id() + " [" + node.status() + "]");
                }

                // Node 2 leaves
                disc2.leave();
                System.out.println("  node-2 leaves");
                Thread.sleep(200);

                status = disc1.status();
                System.out.println("  After node-2 leaves: " + status.memberCount() + " members");
            }
        }
    }

    // ---- Raft Log ----

    private static void demoRaftLog() throws Exception {
        System.out.println("--- Raft Log ---");

        RaftLogEntry entry1 = RaftLogEntry.of(1, 1, RaftLogEntry.EntryType.CONFIG_CHANGE,
                "Add node-A to cluster".getBytes(StandardCharsets.UTF_8));
        System.out.println("  Entry 1: " + entry1);

        RaftLogEntry entry2 = RaftLogEntry.of(1, 2, RaftLogEntry.EntryType.NORMAL,
                "Set /config/replicas=3".getBytes(StandardCharsets.UTF_8));
        System.out.println("  Entry 2: " + entry2);

        RaftLogEntry entry3 = RaftLogEntry.noop(2, 3);
        System.out.println("  Entry 3 (NOOP): " + entry3);

        System.out.println("  Entry types: " +
                String.join(", ",
                        java.util.Arrays.stream(RaftLogEntry.EntryType.values())
                                .map(Enum::name).toArray(String[]::new)));
    }

    // ---- Session ----

    private static void demoSession(EtcdClient client, EtcdKVStore store) throws Exception {
        System.out.println("--- Session (Lease + Auto Keep-Alive) ---");

        try (EtcdSession session = EtcdSession.create(client, 10).join()) {
            EtcdLease lease = session.lease();
            System.out.println("  Session lease ID: " + lease.id());
            System.out.println("  Session TTL: " + lease.ttl());

            // Use the session lease to protect a key
            store.put("/session/my-node", "alive".getBytes(StandardCharsets.UTF_8), lease).join();
            System.out.println("  PUT /session/my-node with lease");

            // Simulate work
            Thread.sleep(500);

            String value = store.getAsString("/session/my-node").join();
            System.out.println("  Value still alive: " + (value != null ? "YES (" + value + ")" : "NO"));
        }

        // After session closes, lease is revoked
        Thread.sleep(100);
        String afterClose = store.getAsString("/session/my-node").join();
        System.out.println("  After session close: " +
                (afterClose == null ? "key deleted (lease revoked)" : "still exists: " + afterClose));
    }
}
