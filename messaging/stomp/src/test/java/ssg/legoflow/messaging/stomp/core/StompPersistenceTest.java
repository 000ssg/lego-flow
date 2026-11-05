package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

class InMemoryStompPersistenceAdapterTest {

    @Test void testSaveAndLoadSession() {
        var adapter = new InMemoryStompPersistenceAdapter();
        var data = new StompPersistenceAdapter.StompSessionData("client1", "admin", "1.2", List.of());
        adapter.saveSession(data);

        var loaded = adapter.loadSession("client1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().sessionId()).isEqualTo("client1");
    }

    @Test void testLoadNonExistentSession() {
        var adapter = new InMemoryStompPersistenceAdapter();
        var loaded = adapter.loadSession("unknown");
        assertThat(loaded).isEmpty();
    }

    @Test void testRemoveSession() {
        var adapter = new InMemoryStompPersistenceAdapter();
        var data = new StompPersistenceAdapter.StompSessionData("client1", "admin", "1.2", List.of());
        adapter.saveSession(data);
        adapter.removeSession("client1");

        var loaded = adapter.loadSession("client1");
        assertThat(loaded).isEmpty();
    }

    @Test void testSaveAndLoadSubscription() {
        var adapter = new InMemoryStompPersistenceAdapter();
        var subData = new StompPersistenceAdapter.StompSubscriptionData("sub1", "/topic/test", "auto");
        var session = new StompPersistenceAdapter.StompSessionData("client1", "admin", "1.2", List.of(subData));
        adapter.saveSession(session);

        var loaded = adapter.loadSession("client1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().subscriptions()).hasSize(1);
        assertThat(loaded.get().subscriptions().get(0).destination()).isEqualTo("/topic/test");
    }

    @Test void testLoadAllSessions() {
        var adapter = new InMemoryStompPersistenceAdapter();
        adapter.saveSession(new StompPersistenceAdapter.StompSessionData("c1", "admin", "1.2", List.of()));
        adapter.saveSession(new StompPersistenceAdapter.StompSessionData("c2", "admin", "1.2", List.of()));

        var all = adapter.loadAllSessions();
        assertThat(all).hasSize(2);
    }
}
