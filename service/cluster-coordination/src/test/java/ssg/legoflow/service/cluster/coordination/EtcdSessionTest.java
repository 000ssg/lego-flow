package ssg.legoflow.service.cluster.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class EtcdSessionTest {

    private EtcdClient client;

    @BeforeEach
    void setUp() {
        EtcdConfig config = EtcdConfig.builder().build();
        client = new EtcdClient(config);
    }

    @Test
    void create_session() throws Exception {
        try (EtcdSession session = EtcdSession.create(client, 30).join()) {
            assertThat(session).isNotNull();
            assertThat(session.isActive()).isTrue();
            assertThat(session.ttlSeconds()).isEqualTo(30);
        }
    }

    @Test
    void create_returnsLease() throws Exception {
        try (EtcdSession session = EtcdSession.create(client, 60).join()) {
            EtcdLease lease = session.lease();
            assertThat(lease).isNotNull();
            assertThat(lease.ttlSeconds()).isEqualTo(60);
        }
    }

    @Test
    void ttl_asDuration() throws Exception {
        try (EtcdSession session = EtcdSession.create(client, 45).join()) {
            assertThat(session.ttl()).isEqualTo(Duration.ofSeconds(45));
        }
    }

    @Test
    void closeAsync_revokesLease() throws Exception {
        EtcdSession session = EtcdSession.create(client, 30).join();
        assertThat(session.isActive()).isTrue();

        session.closeAsync().join();
        assertThat(session.isActive()).isFalse();
    }

    @Test
    void onClosed_listener_notified() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        EtcdSession session = EtcdSession.create(client, 30).join();
        session.onClosed(s -> latch.countDown());

        session.closeAsync().join();
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void close_asyncClose() throws Exception {
        EtcdSession session = EtcdSession.create(client, 30).join();
        session.close();
        assertThat(session.isActive()).isFalse();
    }

    @Test
    void doubleClose_isIdempotent() throws Exception {
        EtcdSession session = EtcdSession.create(client, 30).join();
        session.close();
        assertThatCode(() -> session.close()).doesNotThrowAnyException();
    }

    @Test
    void nullClient_throws() {
        assertThatThrownBy(() -> EtcdSession.create(null, 30).join())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void zeroTTL_throws() {
        assertThatThrownBy(() -> EtcdSession.create(client, 0).join())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeTTL_throws() {
        assertThatThrownBy(() -> EtcdSession.create(client, -1).join())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullListener_throws() {
        EtcdSession session = EtcdSession.create(client, 30).join();
        assertThatThrownBy(() -> session.onClosed(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toString_containsInfo() throws Exception {
        try (EtcdSession session = EtcdSession.create(client, 30).join()) {
            String s = session.toString();
            assertThat(s).contains("EtcdSession");
            assertThat(s).contains("active=true");
        }
    }
}
