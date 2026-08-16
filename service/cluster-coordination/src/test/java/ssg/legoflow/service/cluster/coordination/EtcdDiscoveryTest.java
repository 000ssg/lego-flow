package ssg.legoflow.service.cluster.coordination;

import ssg.legoflow.network.cluster.core.ClusterEvent;
import ssg.legoflow.network.cluster.core.ClusterEventListener;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;
import ssg.legoflow.network.cluster.core.ClusterStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

class EtcdDiscoveryTest {

    private EtcdClient client;
    private EtcdKVStore store;
    private ClusterNode localNode;

    @BeforeEach
    void setUp() {
        EtcdConfig config = EtcdConfig.builder().build();
        client = new EtcdClient(config);
        store = new EtcdKVStore(client);
        localNode = ClusterNode.builder()
                .id("node-1")
                .host("127.0.0.1")
                .port(8080)
                .role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE)
                .build();
    }

    @Test
    void start_registersNode() throws Exception {
        try (EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-service", localNode)) {
            discovery.start().join();

            List<ClusterNode> members = discovery.status().members();
            assertThat(members).hasSize(1);
            assertThat(members.get(0).id()).isEqualTo("node-1");
        }
    }

    @Test
    void status_returnsLocalNode() throws Exception {
        try (EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-service", localNode)) {
            discovery.start().join();

            ClusterNode local = discovery.localNode();
            assertThat(local.id()).isEqualTo("node-1");
            assertThat(local.host()).isEqualTo("127.0.0.1");
            assertThat(local.port()).isEqualTo(8080);
        }
    }

    @Test
    void status_hasLeader() throws Exception {
        try (EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-service", localNode)) {
            discovery.start().join();

            ClusterStatus status = discovery.status();
            assertThat(status.hasLeader()).isTrue();
            assertThat(status.leader().id()).isEqualTo("node-1");
        }
    }

    @Test
    void status_withMultipleNodes() throws Exception {
        ClusterNode node2 = ClusterNode.builder()
                .id("node-2")
                .host("127.0.0.2")
                .port(8081)
                .role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE)
                .build();

        try (EtcdDiscovery d1 = new EtcdDiscovery(store, client, "svc", localNode);
             EtcdDiscovery d2 = new EtcdDiscovery(store, client, "svc", node2)) {
            d1.start().join();
            d2.start().join();

            ClusterStatus status = d1.status();
            assertThat(status.memberCount()).isEqualTo(2);
        }
    }

    @Test
    void leave_removesNode() throws Exception {
        try (EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-service", localNode)) {
            discovery.start().join();

            List<ClusterEvent> events = new CopyOnWriteArrayList<>();
            discovery.addListener(e -> events.add(e));

            discovery.leave();

            ClusterStatus status = discovery.status();
            assertThat(status.memberCount()).isZero();
            assertThat(events).anySatisfy(e -> {
                assertThat(e).isInstanceOf(ClusterEvent.NodeLeft.class);
            });
        }
    }

    @Test
    void leaveAsync_removesNode() throws Exception {
        try (EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-service", localNode)) {
            discovery.start().join();
            discovery.leaveAsync().join();

            ClusterStatus status = discovery.status();
            assertThat(status.memberCount()).isZero();
        }
    }

    @Test
    void addListener_and_removeListener() throws Exception {
        List<ClusterEvent> events = new ArrayList<>();
        ClusterEventListener listener = events::add;

        try (EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-service", localNode)) {
            discovery.addListener(listener);
            discovery.removeListener(listener);

            discovery.start().join();

            // Trigger leave which fires events — removed listener should not receive them
            discovery.leave();

            // Listener was removed, so no events should be in the list
            assertThat(events).isEmpty();
        }
    }

    @Test
    void nullListener_throws() {
        EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-service", localNode);
        assertThatThrownBy(() -> discovery.addListener(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void serviceName_returns() {
        EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-svc", localNode);
        assertThat(discovery.serviceName()).isEqualTo("my-svc");
    }

    @Test
    void client_returns() {
        EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-svc", localNode);
        assertThat(discovery.client()).isEqualTo(client);
    }

    @Test
    void customLeaseTtl() {
        EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-svc", localNode,
                Duration.ofSeconds(10));
        assertThat(discovery).isNotNull();
    }

    @Test
    void nullStore_throws() {
        assertThatThrownBy(() -> new EtcdDiscovery(null, client, "svc", localNode))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullClient_throws() {
        assertThatThrownBy(() -> new EtcdDiscovery(store, null, "svc", localNode))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullServiceName_throws() {
        assertThatThrownBy(() -> new EtcdDiscovery(store, client, null, localNode))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullLocalNode_throws() {
        assertThatThrownBy(() -> new EtcdDiscovery(store, client, "svc", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void start_isIdempotent() throws Exception {
        try (EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-service", localNode)) {
            discovery.start().join();
            assertThatCode(() -> discovery.start().join()).doesNotThrowAnyException();
        }
    }

    @Test
    void leave_isIdempotent() throws Exception {
        try (EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-service", localNode)) {
            discovery.start().join();
            discovery.leave();
            assertThatCode(() -> discovery.leave()).doesNotThrowAnyException();
        }
    }

    @Test
    void toString_containsInfo() {
        EtcdDiscovery discovery = new EtcdDiscovery(store, client, "my-svc", localNode);
        String s = discovery.toString();
        assertThat(s).contains("EtcdDiscovery");
        assertThat(s).contains("my-svc");
        assertThat(s).contains("node-1");
    }
}
