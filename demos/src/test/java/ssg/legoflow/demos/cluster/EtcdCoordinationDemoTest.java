package ssg.legoflow.demos.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.cluster.coordination.EtcdClient;
import ssg.legoflow.service.cluster.coordination.EtcdConfig;
import ssg.legoflow.service.cluster.coordination.EtcdKVStore;
import ssg.legoflow.service.cluster.coordination.EtcdLease;
import ssg.legoflow.service.cluster.coordination.EtcdTransaction;
import ssg.legoflow.service.cluster.coordination.raft.RaftLogEntry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for etcd/Raft coordination components used in {@link EtcdCoordinationDemo}.
 *
 * Tests the underlying coordination primitives (KV store, transactions,
 * leader election, leases, watches) using the in-memory implementation.
 */
class EtcdCoordinationDemoTest {

    // ── Config ──

    @Test
    void testConfigDefaults() {
        var config = EtcdConfig.builder().build();

        assertThat(config.endpoints()).hasSize(1);
        var ep = config.endpoints().get(0);
        assertThat(ep.getHostString()).isEqualTo("localhost");
        assertThat(ep.getPort()).isEqualTo(2379);
        assertThat(config.dialTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.username()).isNull();
        assertThat(config.password()).isNull();
    }

    @Test
    void testConfigCustomValues() {
        var config = EtcdConfig.builder()
                .endpoints(List.of(new InetSocketAddress("10.0.0.1", 2380)))
                .dialTimeout(Duration.ofSeconds(3))
                .requestTimeout(Duration.ofSeconds(8))
                .username("admin")
                .password("secret")
                .build();

        assertThat(config.endpoints()).hasSize(1);
        assertThat(config.dialTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(config.username()).isEqualTo("admin");
        assertThat(config.password()).isEqualTo("secret");
    }

    @Test
    void testConfigRejectsEmptyEndpoints() {
        assertThatThrownBy(() -> EtcdConfig.builder()
                .endpoints(List.of())
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── KV Store ──

    @Test
    void testKeyValueStorePutGet() throws Exception {
        var config = EtcdConfig.builder().build();
        try (var client = new EtcdClient(config)) {
            var store = new EtcdKVStore(client);

            store.put("/test/key", "value".getBytes(StandardCharsets.UTF_8)).join();
            String result = store.getAsString("/test/key").join();
            assertThat(result).isEqualTo("value");
        }
    }

    @Test
    void testKeyValueStoreDelete() throws Exception {
        var config = EtcdConfig.builder().build();
        try (var client = new EtcdClient(config)) {
            var store = new EtcdKVStore(client);

            store.put("/test/key", "value".getBytes(StandardCharsets.UTF_8)).join();
            store.delete("/test/key").join();
            var result = store.get("/test/key").join();
            assertThat(result).isNull();
        }
    }

    @Test
    void testKeyValueStoreRange() throws Exception {
        var config = EtcdConfig.builder().build();
        try (var client = new EtcdClient(config)) {
            var store = new EtcdKVStore(client);

            store.put("/config/a", "1".getBytes(StandardCharsets.UTF_8)).join();
            store.put("/config/b", "2".getBytes(StandardCharsets.UTF_8)).join();
            store.put("/other/c", "3".getBytes(StandardCharsets.UTF_8)).join();

            var entries = store.range("/config/").join();
            assertThat(entries).hasSize(2);
            assertThat(entries.keySet()).contains("/config/a", "/config/b");
        }
    }

    // ── Transactions (CAS) ──

    @Test
    void testCompareAndSwapSuccess() throws Exception {
        var config = EtcdConfig.builder().build();
        try (var client = new EtcdClient(config)) {
            var store = new EtcdKVStore(client);

            store.put("/counter", "0".getBytes(StandardCharsets.UTF_8)).join();

            boolean ok = EtcdTransaction.create(store, "/counter", "0".getBytes(StandardCharsets.UTF_8))
                    .thenPut("/counter", "1".getBytes(StandardCharsets.UTF_8))
                    .execute()
                    .join();
            assertThat(ok).isTrue();

            String value = store.getAsString("/counter").join();
            assertThat(value).isEqualTo("1");
        }
    }

    @Test
    void testCompareAndSwapFailure() throws Exception {
        var config = EtcdConfig.builder().build();
        try (var client = new EtcdClient(config)) {
            var store = new EtcdKVStore(client);

            store.put("/counter", "5".getBytes(StandardCharsets.UTF_8)).join();

            // Stale CAS: expected "0" but actual is "5"
            boolean ok = EtcdTransaction.create(store, "/counter", "0".getBytes(StandardCharsets.UTF_8))
                    .thenPut("/counter", "99".getBytes(StandardCharsets.UTF_8))
                    .execute()
                    .join();
            assertThat(ok).isFalse();

            // Value should be unchanged
            String value = store.getAsString("/counter").join();
            assertThat(value).isEqualTo("5");
        }
    }

    // ── Lease ──

    @Test
    void testLeaseGrantAndRevoke() throws Exception {
        var config = EtcdConfig.builder().build();
        try (var client = new EtcdClient(config)) {
            var lease = EtcdLease.grant(client, 10).join();

            assertThat(lease.id()).isGreaterThan(0);
            assertThat(lease.ttlSeconds()).isEqualTo(10);
            assertThat(lease.isActive()).isTrue();

            lease.revoke().join();
            assertThat(lease.isActive()).isFalse();
        }
    }

    @Test
    void testLeaseRevocationListener() throws Exception {
        var config = EtcdConfig.builder().build();
        try (var client = new EtcdClient(config)) {
            var lease = EtcdLease.grant(client, 30).join();
            var revoked = new CountDownLatch(1);
            lease.onRevoked(l -> revoked.countDown());

            lease.revoke().join();
            assertThat(revoked.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    // ── Watch ──

    @Test
    void testWatchReceivesEvents() throws Exception {
        var config = EtcdConfig.builder().build();
        try (var client = new EtcdClient(config)) {
            var store = new EtcdKVStore(client);
            var events = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            var watcher = new ssg.legoflow.service.cluster.coordination.EtcdWatcher(store, "/config/");
            watcher.onEvent(event -> {
                events.add(event.key() + "=" + new String(event.value() != null ? event.value() : new byte[0], StandardCharsets.UTF_8));
                latch.countDown();
            });
            watcher.start();

            store.put("/config/test", "value".getBytes(StandardCharsets.UTF_8)).join();

            assertThat(latch.await(3, TimeUnit.SECONDS))
                    .as("watcher should detect the put")
                    .isTrue();
            assertThat(events).isNotEmpty();

            watcher.close();
        }
    }

    // ── Raft Log ──

    @Test
    void testRaftLogEntryCreation() {
        var entry = RaftLogEntry.of(1, 1, RaftLogEntry.EntryType.NORMAL,
                "command".getBytes(StandardCharsets.UTF_8));

        assertThat(entry.term()).isEqualTo(1);
        assertThat(entry.index()).isEqualTo(1);
        assertThat(entry.entryType()).isEqualTo(RaftLogEntry.EntryType.NORMAL);
        assertThat(entry.timestamp()).isNotNull();
    }

    @Test
    void testRaftLogNoop() {
        var entry = RaftLogEntry.noop(2, 5);

        assertThat(entry.term()).isEqualTo(2);
        assertThat(entry.index()).isEqualTo(5);
        assertThat(entry.entryType()).isEqualTo(RaftLogEntry.EntryType.NOOP);
        assertThat(entry.data()).isNull();
    }

    @Test
    void testRaftLogConfigChange() {
        var entry = RaftLogEntry.of(1, 1, RaftLogEntry.EntryType.CONFIG_CHANGE,
                "add-node".getBytes(StandardCharsets.UTF_8));

        assertThat(entry.entryType()).isEqualTo(RaftLogEntry.EntryType.CONFIG_CHANGE);
    }

    @Test
    void testRaftLogNegativeTermRejected() {
        assertThatThrownBy(() -> RaftLogEntry.of(-1, 1, RaftLogEntry.EntryType.NORMAL,
                "data".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
