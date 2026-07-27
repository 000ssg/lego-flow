package ssg.legoflow.database.postgresql.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.database.postgresql.protocol.BackendMessage;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link NotificationManager}.
 */
class NotificationManagerTest {

    private NotificationManager manager;

    @BeforeEach
    void setUp() {
        manager = new NotificationManager();
    }

    @Test
    void testListenAndNotify() {
        List<BackendMessage.NotificationResponse> received = new ArrayList<>();
        manager.listen("test_channel", received::add);
        manager.notify(1234, "test_channel", "hello");

        assertThat(received).hasSize(1);
        assertThat(received.get(0).channel()).isEqualTo("test_channel");
        assertThat(received.get(0).payload()).isEqualTo("hello");
        assertThat(received.get(0).processId()).isEqualTo(1234);
    }

    @Test
    void testNotifyNoListeners() {
        // Should not throw
        manager.notify(1234, "test_channel", "hello");
    }

    @Test
    void testMultipleListeners() {
        List<BackendMessage.NotificationResponse> received1 = new ArrayList<>();
        List<BackendMessage.NotificationResponse> received2 = new ArrayList<>();
        manager.listen("ch", received1::add);
        manager.listen("ch", received2::add);

        manager.notify(1, "ch", "msg");

        assertThat(received1).hasSize(1);
        assertThat(received2).hasSize(1);
    }

    @Test
    void testUnlisten() {
        List<BackendMessage.NotificationResponse> received = new ArrayList<>();
        var listener = (java.util.function.Consumer<BackendMessage.NotificationResponse>) received::add;
        manager.listen("ch", listener);
        manager.unlisten("ch", listener);

        manager.notify(1, "ch", "msg");
        assertThat(received).isEmpty();
    }

    @Test
    void testUnlistenAll() {
        List<BackendMessage.NotificationResponse> received = new ArrayList<>();
        var listener = (java.util.function.Consumer<BackendMessage.NotificationResponse>) received::add;
        manager.listen("ch1", listener);
        manager.listen("ch2", listener);

        manager.unlistenAll(listener);

        manager.notify(1, "ch1", "msg");
        manager.notify(1, "ch2", "msg");
        assertThat(received).isEmpty();
    }

    @Test
    void testChannelCount() {
        assertThat(manager.channelCount()).isEqualTo(0);

        List<BackendMessage.NotificationResponse> received = new ArrayList<>();
        manager.listen("ch1", received::add);
        assertThat(manager.channelCount()).isEqualTo(1);

        manager.listen("ch2", received::add);
        assertThat(manager.channelCount()).isEqualTo(2);
    }

    @Test
    void testDifferentChannels() {
        List<BackendMessage.NotificationResponse> received1 = new ArrayList<>();
        List<BackendMessage.NotificationResponse> received2 = new ArrayList<>();
        manager.listen("ch1", received1::add);
        manager.listen("ch2", received2::add);

        manager.notify(1, "ch1", "msg1");

        assertThat(received1).hasSize(1);
        assertThat(received2).isEmpty();
    }

    @Test
    void testEmptyPayload() {
        List<BackendMessage.NotificationResponse> received = new ArrayList<>();
        manager.listen("ch", received::add);
        manager.notify(1, "ch", "");

        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload()).isEmpty();
    }
}
