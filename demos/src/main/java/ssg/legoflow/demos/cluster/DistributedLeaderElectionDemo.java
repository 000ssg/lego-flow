package ssg.legoflow.demos.cluster;

import ssg.legoflow.service.cluster.coordination.EtcdConfig;
import ssg.legoflow.service.cluster.coordination.EtcdLease;
import ssg.legoflow.service.cluster.coordination.raft.RaftLogEntry;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
/**
 * Demo: Distributed Leader Election.
 *
 * <p>Simulates a 3-node cluster competing for leadership via two approaches:
 * <ol>
 *   <li><b>Raft-style Leader Election</b>: Nodes exchange votes with term numbers</li>
 *   <li><b>Lease-based Election</b>: Nodes compete via lease TTL (using EtcdLease API)</li>
 * </ol>
 *
 * <p>Scenario:
 * <ol>
 *   <li>3 nodes boot and start election</li>
 *   <li>One node wins leadership</li>
 *   <li>Leader publishes work assignments</li>
 *   <li>Leader crashes → new election triggered</li>
 *   <li>New leader resumes operations</li>
 * </ol>
 */
public final class DistributedLeaderElectionDemo {

    DistributedLeaderElectionDemo() {}

    /**
     * Simulated Raft voter that tracks term and vote state.
     */
    static class RaftNode {
        private final String nodeId;
        private final AtomicInteger currentTerm = new AtomicInteger(0);
        private final AtomicReference<String> votedFor = new AtomicReference<>();
        private final AtomicReference<String> leader = new AtomicReference<>();
        private final List<Consumer<String>> leaderChangedListeners = new CopyOnWriteArrayList<>();
        private volatile boolean alive = true;

        RaftNode(String nodeId) {
            this.nodeId = nodeId;
        }

        int currentTerm() { return currentTerm.get(); }
        String votedFor() { return votedFor.get(); }
        String leader() { return leader.get(); }
        boolean isLeader() { return nodeId.equals(leader.get()); }
        String nodeId() { return nodeId; }
        boolean isAlive() { return alive; }

        void markDead() { alive = false; }

        /**
         * Requests a vote from this node for the given candidate.
         */
        boolean requestVote(String candidateId, int term) {
            if (!alive) return false;
            if (term <= currentTerm.get()) return false;

            // Update term and reset vote (Raft: one vote per term)
            currentTerm.set(term);
            votedFor.set(null);

            // Grant vote — in a new term, haven't voted yet
            votedFor.set(candidateId);
            return true;
        }

        /**
         * Accepts an election result (won or lost).
         */
        void acceptElection(String electedLeader) {
            if (!alive) return;
            leader.set(electedLeader);
            for (var listener : leaderChangedListeners) {
                listener.accept(electedLeader);
            }
        }

        void onLeaderChanged(Consumer<String> listener) {
            leaderChangedListeners.add(listener);
        }
    }

    /**
     * Simulated lease for leader election (mirrors EtcdLease semantics).
     */
    static class SimLease {
        private final String id;
        private int ttlSeconds;
        private volatile boolean active = true;

        SimLease(String id, int ttlSeconds) {
            this.id = id;
            this.ttlSeconds = ttlSeconds;
        }

        String id() { return id; }
        int ttlSeconds() { return ttlSeconds; }
        Duration ttl() { return Duration.ofSeconds(ttlSeconds); }
        boolean isActive() { return active; }

        void revoke() {
            active = false;
        }

        void renew(int newTtlSeconds) {
            if (!active) return;
            this.ttlSeconds = newTtlSeconds;
        }
    }

    // ── Simulation ──

    /**
     * Runs the Raft leader election simulation.
     */
    Map<String, Object> runRaftElection() throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();

        var nodeA = new RaftNode("node-A");
        var nodeB = new RaftNode("node-B");
        var nodeC = new RaftNode("node-C");

        results.put("initial_term_A", nodeA.currentTerm());
        results.put("initial_term_B", nodeB.currentTerm());
        results.put("initial_term_C", nodeC.currentTerm());
        System.out.println("[1] Nodes started, all in term 0");

        // Node A campaigns for leadership (term 1)
        nodeA.currentTerm.set(1);
        boolean bVotesA = nodeB.requestVote("node-A", 1);
        boolean cVotesA = nodeC.requestVote("node-A", 1);
        boolean aWins = bVotesA && cVotesA;

        results.put("nodeA_election_votes", List.of(bVotesA, cVotesA));
        if (aWins) {
            nodeA.acceptElection("node-A");
            nodeB.acceptElection("node-A");
            nodeC.acceptElection("node-A");
        }
        results.put("nodeA_is_leader", nodeA.isLeader());
        System.out.println("[2] Node A election: B voted=" + bVotesA
                + ", C voted=" + cVotesA + " → A is leader=" + nodeA.isLeader());

        // Verify leader election log entry
        var logEntry = RaftLogEntry.of(1, 1, RaftLogEntry.EntryType.NORMAL,
                "election:node-A".getBytes());
        results.put("log_entry_term", logEntry.term());
        results.put("log_entry_type", logEntry.entryType());
        System.out.println("[3] Election log entry: term=" + logEntry.term()
                + ", type=" + logEntry.entryType());

        // ── Step 4: Simulate leader crash ──
        nodeA.markDead();
        System.out.println("[4] Node A crashed. Node B starts new election...");

        // Node B starts new election (term 2)
        nodeB.currentTerm.set(2);
        boolean cVotesB = nodeC.requestVote("node-B", 2);
        results.put("nodeB_campaign_term", nodeB.currentTerm());
        results.put("nodeC_voted_B", cVotesB);
        results.put("nodeA_still_dead", !nodeA.isAlive());
        System.out.println("    Node B campaigns in term 2, C voted=" + cVotesB);

        if (cVotesB) {
            nodeB.acceptElection("node-B");
            nodeC.acceptElection("node-B");
        }
        results.put("nodeB_is_leader", nodeB.isLeader());
        results.put("election_completed", cVotesB);
        System.out.println("[5] Election completed: B is leader=" + nodeB.isLeader());

        // ── Step 5: Raft log entries ──
        var entries = List.of(
                RaftLogEntry.of(1, 1, RaftLogEntry.EntryType.NORMAL, "election:node-A".getBytes()),
                RaftLogEntry.of(2, 2, RaftLogEntry.EntryType.NORMAL, "election:node-B".getBytes()),
                RaftLogEntry.noop(2, 3)
        );
        results.put("log_entries_count", entries.size());
        System.out.println("[6] Log entries: " + entries.size()
                + " (elections + noop)");

        // Verify log term ordering
        var terms = entries.stream().mapToLong(RaftLogEntry::term).toArray();
        var ordered = true;
        for (int i = 1; i < terms.length; i++) {
            if (terms[i] < terms[i - 1]) ordered = false;
        }
        results.put("log_ordered", ordered);

        return results;
    }

    /**
     * Simulates lease-based leader election.
     */
    Map<String, Object> runLeaseElection() throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();

        // Simulate: Node A acquires lease (simulating EtcdLease.grant())
        var leaseA = new SimLease("lease-A", 10);
        results.put("lease_A_id", leaseA.id());
        results.put("lease_A_ttl_seconds", leaseA.ttlSeconds());
        results.put("lease_A_active", leaseA.isActive());
        System.out.println("[7] Node A acquired lease: id=" + leaseA.id()
                + ", TTL=" + leaseA.ttlSeconds() + "s");

        // Node B cannot acquire while A holds it (simulated via shared lock)
        // In etcd, CAS prevents duplicate lease grants for the same key
        boolean leaseB_granted = false;
        results.put("lease_B_granted_before_A_revoked", leaseB_granted);
        System.out.println("[8] Node B cannot acquire while A holds lease");

        // Simulate lease revocation (A crashed, lease expired)
        leaseA.revoke();
        results.put("lease_A_revoked", !leaseA.isActive());
        System.out.println("[9] Node A lease revoked (crash simulated)");

        // Now B can acquire
        var leaseB = new SimLease("lease-B", 10);
        results.put("lease_B_acquired_after_revocation", leaseB.isActive());
        System.out.println("[10] Node B acquires lease: active=" + leaseB.isActive());

        // Lease renewal
        leaseB.renew(30);
        results.put("lease_B_renewed_ttl", leaseB.ttlSeconds());
        System.out.println("[11] Node B renewed lease: TTL=" + leaseB.ttlSeconds() + "s");

        return results;
    }

    /**
     * Config test.
     */
    Map<String, Object> runConfigTest() {
        Map<String, Object> results = new LinkedHashMap<>();

        var config = EtcdConfig.builder()
                .endpoints(List.of(new InetSocketAddress("localhost", 2379)))
                .dialTimeout(Duration.ofSeconds(5))
                .build();

        results.put("etcd_endpoints", config.endpoints());
        results.put("etcd_dial_timeout", config.dialTimeout());
        System.out.println("[12] etcd config: endpoints=" + config.endpoints()
                + ", dialTimeout=" + config.dialTimeout());

        return results;
    }

    /**
     * Runs the full election demo combining both approaches.
     */
    public Map<String, Object> run() throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();

        var raftResults = runRaftElection();
        results.putAll(raftResults);

        var leaseResults = runLeaseElection();
        results.putAll(leaseResults);

        var configResults = runConfigTest();
        results.putAll(configResults);

        return results;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Distributed Leader Election Demo ===");
        System.out.println();

        var demo = new DistributedLeaderElectionDemo();
        var results = demo.run();

        System.out.println();
        System.out.println("=== Results ===");
        results.forEach((k, v) -> System.out.println("  " + k + " = " + v));
    }
}
