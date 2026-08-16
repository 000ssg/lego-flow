package ssg.legoflow.service.cluster.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class EtcdWatcherTest {

    private EtcdClient client;
    private EtcdKVStore store;

    @BeforeEach
    void setUp() {
        EtcdConfig config = EtcdConfig.builder().build();
        client = new EtcdClient(config);
        store = new EtcdKVStore(client);
    }

    @Test
    void watch_detectsChanges() throws Exception {
        List<EtcdWatcher.WatchEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (EtcdWatcher watcher = new EtcdWatcher(store, "/prefix/")) {
            watcher.onEvent(event -> {
                events.add(event);
                latch.countDown();
            });
            watcher.start();

            store.put("/prefix/key", "value".getBytes(StandardCharsets.UTF_8)).join();

            assertThat(latch.await(3, TimeUnit.SECONDS))
                    .as("watcher should detect the put")
                    .isTrue();
            assertThat(events).isNotEmpty();
        }
    }

    @Test
    void watch_events_haveRevision() throws Exception {
        List<EtcdWatcher.WatchEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (EtcdWatcher watcher = new EtcdWatcher(store, "/prefix/")) {
            watcher.onEvent(event -> {
                events.add(event);
                latch.countDown();
            });
            watcher.start();

            store.put("/prefix/key", "value".getBytes(StandardCharsets.UTF_8)).join();

            assertThat(latch.await(3, TimeUnit.SECONDS))
                    .as("watcher should detect the put")
                    .isTrue();

            for (EtcdWatcher.WatchEvent e : events) {
                assertThat(e.revision()).isPositive();
            }
        }
    }

    @Test
    void watch_events_arePut() throws Exception {
        List<EtcdWatcher.WatchEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (EtcdWatcher watcher = new EtcdWatcher(store, "/prefix/")) {
            watcher.onEvent(event -> {
                events.add(event);
                latch.countDown();
            });
            watcher.start();

            store.put("/prefix/key", "value".getBytes(StandardCharsets.UTF_8)).join();

            assertThat(latch.await(3, TimeUnit.SECONDS))
                    .as("watcher should detect the put")
                    .isTrue();

            assertThat(events.stream().allMatch(e -> e.type() == EtcdWatcher.EventType.PUT))
                    .isTrue();
        }
    }

    @Test
    void watch_lastRevision_initiallyMatchesStore() {
        EtcdWatcher watcher = new EtcdWatcher(store, "/prefix/");
        assertThat(watcher.lastRevision()).isEqualTo(store.revision());
    }

    @Test
    void watch_keyPrefix_returns() {
        EtcdWatcher watcher = new EtcdWatcher(store, "/my-prefix/");
        assertThat(watcher.keyPrefix()).isEqualTo("/my-prefix/");
    }

    @Test
    void watch_nullStore_throws() {
        assertThatThrownBy(() -> new EtcdWatcher(null, "/prefix/"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void watch_nullListener_throws() {
        EtcdWatcher watcher = new EtcdWatcher(store, "/prefix/");
        assertThatThrownBy(() -> watcher.onEvent(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void watch_close_stopsWatching() throws Exception {
        EtcdWatcher watcher = new EtcdWatcher(store, "/prefix/");
        List<EtcdWatcher.WatchEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        watcher.onEvent(event -> {
            events.add(event);
            latch.countDown();
        });
        watcher.start();
        watcher.close();

        // Put something after close — should NOT trigger events
        store.put("/prefix/key", "value".getBytes(StandardCharsets.UTF_8)).join();
        assertThat(latch.await(1, TimeUnit.SECONDS))
                .as("no events should fire after close")
                .isFalse();

        assertThat(events).isEmpty();
    }

    @Test
    void watchEvent_toString() {
        EtcdWatcher.WatchEvent event = new EtcdWatcher.WatchEvent(
                EtcdWatcher.EventType.PUT, "/key", "val".getBytes(StandardCharsets.UTF_8), 1);
        String s = event.toString();
        assertThat(s).contains("WatchEvent");
        assertThat(s).contains("PUT");
        assertThat(s).contains("/key");
        assertThat(s).contains("rev=1");
    }

    @Test
    void eventType_values() {
        assertThat(EtcdWatcher.EventType.PUT.name()).isEqualTo("PUT");
        assertThat(EtcdWatcher.EventType.DELETE.name()).isEqualTo("DELETE");
    }

    @Test
    void start_isIdempotent() throws Exception {
        EtcdWatcher watcher = new EtcdWatcher(store, "/prefix/");
        watcher.start();
        assertThatCode(() -> watcher.start()).doesNotThrowAnyException();
        watcher.close();
    }

    @Test
    void close_clearsListeners() throws Exception {
        EtcdWatcher watcher = new EtcdWatcher(store, "/prefix/");
        List<EtcdWatcher.WatchEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        watcher.onEvent(event -> {
            events.add(event);
            latch.countDown();
        });
        watcher.start();

        // Trigger a change so events fire while watching
        store.put("/prefix/key", "val".getBytes(StandardCharsets.UTF_8)).join();

        assertThat(latch.await(3, TimeUnit.SECONDS))
                .as("watcher should detect the put")
                .isTrue();
        assertThat(events).isNotEmpty();

        // Close clears listeners and stops the scheduler
        watcher.close();

        // After close, adding a new listener yields no events because
        // the scheduler is shut down and watching=false
        List<EtcdWatcher.WatchEvent> afterClose = new CopyOnWriteArrayList<>();
        CountDownLatch afterLatch = new CountDownLatch(1);
        watcher.onEvent(event -> {
            afterClose.add(event);
            afterLatch.countDown();
        });

        // Make another change — no events should fire
        store.put("/prefix/key2", "v2".getBytes(StandardCharsets.UTF_8)).join();
        assertThat(afterLatch.await(1, TimeUnit.SECONDS))
                .as("no events should fire for listeners added after close")
                .isFalse();
        assertThat(afterClose).isEmpty();
    }
}
