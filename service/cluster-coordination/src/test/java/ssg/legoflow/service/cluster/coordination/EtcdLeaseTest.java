package ssg.legoflow.service.cluster.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class EtcdLeaseTest {

    private EtcdClient client;

    @BeforeEach
    void setUp() {
        EtcdConfig config = EtcdConfig.builder().build();
        client = new EtcdClient(config);
    }

    @Test
    void grant_returnsLease() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        assertThat(lease).isNotNull();
        assertThat(lease.id()).isGreaterThan(0);
        assertThat(lease.ttlSeconds()).isEqualTo(30);
        assertThat(lease.isActive()).isTrue();
    }

    @Test
    void grant_nullClient_throws() {
        assertThatThrownBy(() -> EtcdLease.grant(null, 30).join())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void grant_zeroTTL_throws() {
        assertThatThrownBy(() -> EtcdLease.grant(client, 0).join())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grant_negativeTTL_throws() {
        assertThatThrownBy(() -> EtcdLease.grant(client, -1).join())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ttl_asDuration() {
        EtcdLease lease = EtcdLease.grant(client, 60).join();
        assertThat(lease.ttl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(lease.ttlSeconds()).isEqualTo(60);
    }

    @Test
    void startKeepAlive() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        assertThatCode(() -> lease.startKeepAlive().join()).doesNotThrowAnyException();
        assertThat(lease.isActive()).isTrue();
    }

    @Test
    void startKeepAlive_onRevoked_throws() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        lease.revoke().join();

        assertThatThrownBy(() -> lease.startKeepAlive().join())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void revoke_setsInactive() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        lease.revoke().join();

        assertThat(lease.isActive()).isFalse();
    }

    @Test
    void revoke_isIdempotent() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        lease.revoke().join();
        assertThatCode(() -> lease.revoke().join()).doesNotThrowAnyException();
        assertThat(lease.isActive()).isFalse();
    }

    @Test
    void onRevoked_listener_notified() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        EtcdLease lease = EtcdLease.grant(client, 30).join();
        lease.onRevoked(l -> latch.countDown());

        lease.revoke().join();
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void onRevoked_multipleListeners() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        EtcdLease lease = EtcdLease.grant(client, 30).join();
        lease.onRevoked(l -> latch1.countDown());
        lease.onRevoked(l -> latch2.countDown());

        lease.revoke().join();
        assertThat(latch1.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(latch2.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void close_revokesLease() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        lease.close();

        assertThat(lease.isActive()).isFalse();
    }

    @Test
    void id_isPositive() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        assertThat(lease.id()).isPositive();
    }

    @Test
    void uniqueIds() {
        EtcdLease lease1 = EtcdLease.grant(client, 30).join();
        EtcdLease lease2 = EtcdLease.grant(client, 30).join();
        assertThat(lease1.id()).isNotEqualTo(lease2.id());
    }

    @Test
    void toString_containsId() {
        EtcdLease lease = EtcdLease.grant(client, 60).join();
        String s = lease.toString();
        assertThat(s).contains("EtcdLease");
        assertThat(s).contains("id=");
        assertThat(s).contains("ttl=60s");
    }

    @Test
    void onRevoked_nullListener_throws() {
        EtcdLease lease = EtcdLease.grant(client, 30).join();
        assertThatThrownBy(() -> lease.onRevoked(null))
                .isInstanceOf(NullPointerException.class);
    }
}
