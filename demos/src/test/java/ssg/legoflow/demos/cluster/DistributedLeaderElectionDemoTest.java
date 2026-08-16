package ssg.legoflow.demos.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DistributedLeaderElectionDemo}.
 *
 * Verifies that the leader election simulation:
 * - Elects a leader among 3 competing nodes via Raft-style voting
 * - Handles leader failure and re-election
 * - Tracks term numbers monotonically
 * - Manages lease-based election with revocation and re-acquisition
 * - Maintains Raft log ordering
 */
class DistributedLeaderElectionDemoTest {

    @Test
    void testDemoRunsSuccessfully() throws Exception {
        var demo = new DistributedLeaderElectionDemo();
        var result = demo.run();

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }

    @Test
    void testInitialTermsAreZero() throws Exception {
        var demo = new DistributedLeaderElectionDemo();
        var result = demo.run();

        int termA = (int) result.get("initial_term_A");
        int termB = (int) result.get("initial_term_B");
        int termC = (int) result.get("initial_term_C");
        assertThat(termA).isEqualTo(0);
        assertThat(termB).isEqualTo(0);
        assertThat(termC).isEqualTo(0);
    }

    @Test
    void testNodeAElectsAsLeader() throws Exception {
        var demo = new DistributedLeaderElectionDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        List<Boolean> votes = (List<Boolean>) result.get("nodeA_election_votes");
        assertThat(votes).hasSize(2);
        assertThat(votes.get(0)).isTrue();
        assertThat(votes.get(1)).isTrue();

        boolean nodeAIsLeader = (boolean) result.get("nodeA_is_leader");
        assertThat(nodeAIsLeader).isTrue();
    }

    @Test
    void testTermIncreases() throws Exception {
        var demo = new DistributedLeaderElectionDemo();
        var result = demo.run();

        int nodeBCampaignTerm = (int) result.get("nodeB_campaign_term");
        assertThat(nodeBCampaignTerm).isEqualTo(2);
    }

    @Test
    void testLeaderFailureAndReElection() throws Exception {
        var demo = new DistributedLeaderElectionDemo();
        var result = demo.run();

        boolean nodeAStillDead = (boolean) result.get("nodeA_still_dead");
        boolean cVotesB = (boolean) result.get("nodeC_voted_B");
        assertThat(nodeAStillDead).isTrue();
        assertThat(cVotesB).isTrue();

        boolean nodeBIsLeader = (boolean) result.get("nodeB_is_leader");
        assertThat(nodeBIsLeader).isTrue();
    }

    @Test
    void testRaftLogEntriesOrdered() throws Exception {
        var demo = new DistributedLeaderElectionDemo();
        var result = demo.run();

        int logEntriesCount = (int) result.get("log_entries_count");
        boolean logOrdered = (boolean) result.get("log_ordered");
        assertThat(logEntriesCount).isEqualTo(3);
        assertThat(logOrdered).isTrue();
    }

    @Test
    void testLeaseBasedElection() throws Exception {
        var demo = new DistributedLeaderElectionDemo();
        var result = demo.run();

        String leaseAId = (String) result.get("lease_A_id");
        int leaseATtl = (int) result.get("lease_A_ttl_seconds");
        boolean leaseAActive = (boolean) result.get("lease_A_active");
        boolean leaseBGrantedBefore = (boolean) result.get("lease_B_granted_before_A_revoked");
        boolean leaseARevoked = (boolean) result.get("lease_A_revoked");
        boolean leaseBAcquired = (boolean) result.get("lease_B_acquired_after_revocation");
        int leaseBRenewed = (int) result.get("lease_B_renewed_ttl");

        assertThat(leaseAId).isEqualTo("lease-A");
        assertThat(leaseATtl).isEqualTo(10);
        assertThat(leaseAActive).isTrue();
        assertThat(leaseBGrantedBefore).isFalse();
        assertThat(leaseARevoked).isTrue();
        assertThat(leaseBAcquired).isTrue();
        assertThat(leaseBRenewed).isEqualTo(30);
    }

    @Test
    void testEtcdConfig() throws Exception {
        var demo = new DistributedLeaderElectionDemo();
        var result = demo.run();

        var endpoints = result.get("etcd_endpoints");
        var timeout = result.get("etcd_dial_timeout");
        assertThat(endpoints).isNotNull();
        assertThat(timeout).isNotNull();
    }
}
