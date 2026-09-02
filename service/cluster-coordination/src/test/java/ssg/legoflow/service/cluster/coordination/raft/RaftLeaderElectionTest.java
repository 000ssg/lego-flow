package ssg.legoflow.service.cluster.coordination.raft;

import ssg.legoflow.service.cluster.coordination.EtcdClient;
import ssg.legoflow.service.cluster.coordination.EtcdConfig;
import ssg.legoflow.service.cluster.coordination.EtcdElection;
import ssg.legoflow.service.cluster.coordination.EtcdKVStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
class RaftLeaderElectionTest {

    private EtcdClient client;
    private EtcdKVStore store;

    @BeforeEach
    void setUp() {
        EtcdConfig config = EtcdConfig.builder().build();
        client = new EtcdClient(config);
        store = new EtcdKVStore(client);
    }

    @Test
    void campaign_firstNodeWins() throws Exception {
        try (RaftLeaderElection election = new RaftLeaderElection(client, store, "group", "node-1", 30)) {
            boolean won = election.campaign().join();
            assertThat(won).isTrue();
            assertThat(election.isLeader()).isTrue();
        }
    }

    @Test
    void term_incrementsOnCampaign() throws Exception {
        try (RaftLeaderElection election = new RaftLeaderElection(client, store, "group", "node-1", 30)) {
            assertThat(election.term()).isZero();
            election.campaign().join();
            assertThat(election.term()).isEqualTo(1);

            election.resign().join();
            election.campaign().join();
            assertThat(election.term()).isEqualTo(2);
        }
    }

    @Test
    void resign_then_newCampaign() throws Exception {
        try (RaftLeaderElection e1 = new RaftLeaderElection(client, store, "group", "node-1", 30)) {
            e1.campaign().join();
            assertThat(e1.isLeader()).isTrue();
            e1.resign().join();
            assertThat(e1.isLeader()).isFalse();

            try (RaftLeaderElection e2 = new RaftLeaderElection(client, store, "group", "node-2", 30)) {
                e2.campaign().join();
                assertThat(e2.isLeader()).isTrue();
            }
        }
    }

    @Test
    void observeLeader() throws Exception {
        try (RaftLeaderElection e1 = new RaftLeaderElection(client, store, "group", "node-1", 30)) {
            e1.campaign().join();

            try (RaftLeaderElection e2 = new RaftLeaderElection(client, store, "group", "node-2", 30)) {
                EtcdElection.Leader leader = e2.observeLeader().join();
                assertThat(leader).isNotNull();
                assertThat(leader.nodeId()).isEqualTo("node-1");
            }
        }
    }

    @Test
    void onLeaderChanged_listener_notified() throws Exception {
        List<RaftLeaderElection> notifications = new CopyOnWriteArrayList<>();

        try (RaftLeaderElection election = new RaftLeaderElection(client, store, "group", "node-1", 30)) {
            election.onLeaderChanged(notifications::add);
            election.campaign().join();

            assertThat(notifications).isNotEmpty();
        }
    }

    @Test
    void nodeId_returns() {
        RaftLeaderElection election = new RaftLeaderElection(client, store, "group", "node-1", 30);
        assertThat(election.nodeId()).isEqualTo("node-1");
    }

    @Test
    void electionGroup_returns() {
        RaftLeaderElection election = new RaftLeaderElection(client, store, "my-group", "node-1", 30);
        assertThat(election.electionGroup()).isEqualTo("my-group");
    }

    @Test
    void nullClient_throws() {
        assertThatThrownBy(() -> new RaftLeaderElection(null, store, "group", "node", 30))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullStore_throws() {
        assertThatThrownBy(() -> new RaftLeaderElection(client, null, "group", "node", 30))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullGroup_throws() {
        assertThatThrownBy(() -> new RaftLeaderElection(client, store, null, "node", 30))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullNodeId_throws() {
        assertThatThrownBy(() -> new RaftLeaderElection(client, store, "group", null, 30))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullListener_throws() {
        RaftLeaderElection election = new RaftLeaderElection(client, store, "group", "node", 30);
        assertThatThrownBy(() -> election.onLeaderChanged(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resign_withoutLeadership_isNoop() {
        RaftLeaderElection election = new RaftLeaderElection(client, store, "group", "node", 30);
        assertThatCode(() -> election.resign().join()).doesNotThrowAnyException();
    }

    @Test
    void observeLeader_createsElectionIfNull() throws Exception {
        RaftLeaderElection election = new RaftLeaderElection(client, store, "group", "node-1", 30);
        EtcdElection.Leader leader = election.observeLeader().join();
        assertThat(leader).isNull(); // No leader yet
        election.close();
    }

    @Test
    void close_resignsLeader() throws Exception {
        try (RaftLeaderElection election = new RaftLeaderElection(client, store, "group", "node-1", 30)) {
            election.campaign().join();
        }

        // Verify leader key is gone
        EtcdElection.Leader leader = new RaftLeaderElection(client, store, "group", "node-2", 30)
                .observeLeader().join();
        assertThat(leader).isNull();
    }

    @Test
    void toString_containsInfo() {
        try (RaftLeaderElection election = new RaftLeaderElection(client, store, "group", "node-1", 30)) {
            election.campaign().join();
            String s = election.toString();
            assertThat(s).contains("RaftLeaderElection");
            assertThat(s).contains("group");
            assertThat(s).contains("node-1");
            assertThat(s).contains("term=1");
        }
    }
}
