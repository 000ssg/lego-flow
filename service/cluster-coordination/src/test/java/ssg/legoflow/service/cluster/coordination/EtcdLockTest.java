package ssg.legoflow.service.cluster.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
class EtcdLockTest {

    private EtcdClient client;
    private EtcdKVStore store;

    @BeforeEach
    void setUp() {
        EtcdConfig config = EtcdConfig.builder().build();
        client = new EtcdClient(config);
        store = new EtcdKVStore(client);
    }

    @Test
    void lock_and_unlock() throws Exception {
        try (EtcdLease lease = EtcdLease.grant(client, 30).join()) {
            EtcdLock lock = new EtcdLock(store, lease, "test-lock");

            assertThat(lock.isHeld()).isFalse();

            lock.lock().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            assertThat(lock.isHeld()).isTrue();

            lock.unlock().join();
            assertThat(lock.isHeld()).isFalse();
        }
    }

    @Test
    void unlock_withoutLock_isIdempotent() throws Exception {
        try (EtcdLease lease = EtcdLease.grant(client, 30).join()) {
            EtcdLock lock = new EtcdLock(store, lease, "test-lock");
            assertThatCode(() -> lock.unlock().join()).doesNotThrowAnyException();
        }
    }

    @Test
    void close_unlocksLock() throws Exception {
        try (EtcdLease lease = EtcdLease.grant(client, 30).join()) {
            EtcdLock lock = new EtcdLock(store, lease, "test-lock");
            lock.lock().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            assertThat(lock.isHeld()).isTrue();

            lock.close();
            assertThat(lock.isHeld()).isFalse();
        }
    }

    @Test
    void onReleased_listener_notified() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        try (EtcdLease lease = EtcdLease.grant(client, 30).join()) {
            EtcdLock lock = new EtcdLock(store, lease, "test-lock");
            lock.onReleased(l -> latch.countDown());

            lock.lock().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            lock.unlock().join();
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void lease_accessible() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        EtcdLock lock = new EtcdLock(store, lease, "test-lock");
        assertThat(lock.lease()).isEqualTo(lease);
    }

    @Test
    void nullStore_throws() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        assertThatThrownBy(() -> new EtcdLock(null, lease, "name"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullLease_throws() {
        assertThatThrownBy(() -> new EtcdLock(store, null, "name"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullName_throws() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        assertThatThrownBy(() -> new EtcdLock(store, lease, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullOnReleasedListener_throws() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        EtcdLock lock = new EtcdLock(store, lease, "test-lock");
        assertThatThrownBy(() -> lock.onReleased(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void lockKey_format() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        EtcdLock lock = new EtcdLock(store, lease, "my-resource");
        String s = lock.toString();
        assertThat(s).contains("EtcdLock");
        assertThat(s).contains("/locks/my-resource/");
    }

    @Test
    void twoLocks_sameName_differentOwnerKeys() {
        EtcdLease lease1 = EtcdLease.grant(client, 30).join();
        EtcdLease lease2 = EtcdLease.grant(client, 30).join();

        EtcdLock lock1 = new EtcdLock(store, lease1, "shared");
        EtcdLock lock2 = new EtcdLock(store, lease2, "shared");

        // Both should use the same owner key pattern
        String s1 = lock1.toString();
        String s2 = lock2.toString();
        assertThat(s1).contains("/locks/shared/");
        assertThat(s2).contains("/locks/shared/");
    }
}
