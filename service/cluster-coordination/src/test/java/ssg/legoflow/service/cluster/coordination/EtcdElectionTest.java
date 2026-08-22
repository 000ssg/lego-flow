package ssg.legoflow.service.cluster.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
class EtcdElectionTest {

    private EtcdClient client;
    private EtcdKVStore store;

    @BeforeEach
    void setUp() {
        EtcdConfig config = EtcdConfig.builder().build();
        client = new EtcdClient(config);
        store = new EtcdKVStore(client);
    }

    @Test
    void first_campaign_wins() throws Exception {
        try (EtcdElection election = new EtcdElection(client, store, "my-election", "node-1")) {
            boolean won = election.campaign(30).join();
            assertThat(won).isTrue();
            assertThat(election.isLeader()).isTrue();
        }
    }

    @Test
    void second_campaign_loses() throws Exception {
        try (EtcdElection e1 = new EtcdElection(client, store, "my-election", "node-1")) {
            e1.campaign(30).join();

            try (EtcdElection e2 = new EtcdElection(client, store, "my-election", "node-2")) {
                boolean won = e2.campaign(30).join();
                assertThat(won).isFalse();
                assertThat(e2.isLeader()).isFalse();
            }
        }
    }

    @Test
    void resign_allows_new_campaign() throws Exception {
        try (EtcdElection e1 = new EtcdElection(client, store, "group", "node-1")) {
            e1.campaign(30).join();
            e1.resign().join();
            assertThat(e1.isLeader()).isFalse();

            try (EtcdElection e2 = new EtcdElection(client, store, "group", "node-2")) {
                boolean won = e2.campaign(30).join();
                assertThat(won).isTrue();
                assertThat(e2.isLeader()).isTrue();
            }
        }
    }

    @Test
    void observe_returnsLeader() throws Exception {
        try (EtcdElection e1 = new EtcdElection(client, store, "group", "node-1")) {
            e1.campaign(30).join();

            EtcdElection.Leader leader = e1.observe().join();
            assertThat(leader).isNotNull();
            assertThat(leader.nodeId()).isEqualTo("node-1");
            assertThat(leader.elected()).isNotNull();
        }
    }

    @Test
    void observe_noLeader_returnsNull() {
        EtcdElection election = new EtcdElection(client, store, "empty", "node-1");
        EtcdElection.Leader leader = election.observe().join();
        assertThat(leader).isNull();
    }

    @Test
    void onLeaderChanged_listener_notified() throws Exception {
        List<EtcdElection.Leader> leaders = new CopyOnWriteArrayList<>();

        try (EtcdElection election = new EtcdElection(client, store, "group", "node-1")) {
            election.onLeaderChanged(leaders::add);
            election.campaign(30).join();

            assertThat(leaders).hasSize(1);
            assertThat(leaders.get(0).nodeId()).isEqualTo("node-1");
        }
    }

    @Test
    void resign_without_leadership_isNoop() {
        EtcdElection election = new EtcdElection(client, store, "group", "node-1");
        assertThatCode(() -> election.resign().join()).doesNotThrowAnyException();
    }

    @Test
    void campaign_zeroTTL_throws() {
        EtcdElection election = new EtcdElection(client, store, "group", "node-1");
        assertThatThrownBy(() -> election.campaign(0).join())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void campaign_negativeTTL_throws() {
        EtcdElection election = new EtcdElection(client, store, "group", "node-1");
        assertThatThrownBy(() -> election.campaign(-1).join())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullClient_throws() {
        assertThatThrownBy(() -> new EtcdElection(null, store, "group", "node"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullStore_throws() {
        assertThatThrownBy(() -> new EtcdElection(client, null, "group", "node"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullElectionName_throws() {
        assertThatThrownBy(() -> new EtcdElection(client, store, null, "node"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullNodeId_throws() {
        assertThatThrownBy(() -> new EtcdElection(client, store, "group", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullOnLeaderChangedListener_throws() {
        EtcdElection election = new EtcdElection(client, store, "group", "node-1");
        assertThatThrownBy(() -> election.onLeaderChanged(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void electionName_returns() {
        EtcdElection election = new EtcdElection(client, store, "my-group", "node-1");
        assertThat(election.electionName()).isEqualTo("my-group");
    }

    @Test
    void close_resignsLeader() throws Exception {
        try (EtcdElection election = new EtcdElection(client, store, "group", "node-1")) {
            election.campaign(30).join();
        }
        // After close, the leader key should be gone
        EtcdElection.Leader leader = new EtcdElection(client, store, "group", "node-2")
                .observe().join();
        assertThat(leader).isNull();
    }

    @Test
    void leader_record_toString() {
        Instant now = Instant.now();
        EtcdElection.Leader leader = new EtcdElection.Leader("node-1", now);
        String s = leader.toString();
        assertThat(s).contains("Leader{");
        assertThat(s).contains("node-1");
    }

    @Test
    void toString_containsInfo() {
        try (EtcdElection election = new EtcdElection(client, store, "group", "node-1")) {
            election.campaign(30).join();
            String s = election.toString();
            assertThat(s).contains("EtcdElection");
            assertThat(s).contains("group");
            assertThat(s).contains("node-1");
            assertThat(s).contains("amLeader=true");
        }
    }
}
