package ssg.legoflow.http.auth.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InMemorySessionStoreTest {

    private InMemorySessionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySessionStore();
    }

    @Test
    void testCreate() {
        var session = store.create("test-id");
        assertThat(session.getId()).isEqualTo("test-id");
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void testGet() {
        store.create("id1");
        assertThat(store.get("id1")).isPresent();
        assertThat(store.get("nonexistent")).isEmpty();
        assertThat(store.get(null)).isEmpty();
    }

    @Test
    void testRemove() {
        store.create("id1");
        store.remove("id1");
        assertThat(store.get("id1")).isEmpty();
        assertThat(store.size()).isEqualTo(0);
    }

    @Test
    void testRemoveExpired() {
        store.create("id1");
        store.create("id2");
        // With 0 timeout, all sessions are immediately expired
        store.removeExpired(0);
        assertThat(store.size()).isEqualTo(0);
    }

    @Test
    void testAll() {
        store.create("a");
        store.create("b");
        assertThat(store.all()).hasSize(2);
    }

    @Test
    void testInvalidatedSessionNotReturned() {
        var session = store.create("id1");
        session.invalidate();
        assertThat(store.get("id1")).isEmpty();
    }
}
