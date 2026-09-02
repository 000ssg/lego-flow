package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
class IdleNotifierExtendedTest {

    @Test void registerAndNotifyExists() {
        var notifier = new IdleNotifier();
        var counter = new AtomicInteger(0);
        Consumer<String> listener = (s) -> counter.incrementAndGet();
        
        notifier.register("INBOX", listener);
        notifier.notifyExists("INBOX", 5);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test void notifyExpunge() {
        var notifier = new IdleNotifier();
        var called = new AtomicInteger(0);
        notifier.register("INBOX", (s) -> called.incrementAndGet());
        notifier.notifyExpunge("INBOX", 3);
        assertThat(called.get()).isEqualTo(1);
    }

    @Test void notifyFlagChange() {
        var notifier = new IdleNotifier();
        var called = new AtomicInteger(0);
        notifier.register("INBOX", (s) -> called.incrementAndGet());
        notifier.notifyFlagChange("INBOX", 1, Set.of("\\Seen"));
        assertThat(called.get()).isEqualTo(1);
    }

    @Test void unregister() {
        var notifier = new IdleNotifier();
        var counter = new AtomicInteger(0);
        Consumer<String> listener = (s) -> counter.incrementAndGet();
        
        notifier.register("INBOX", listener);
        notifier.unregister("INBOX", listener);
        notifier.notifyExists("INBOX", 1);
        assertThat(counter.get()).isEqualTo(0);  // Should not be notified after unregister
    }

    @Test void notifyWrongMailbox() {
        var notifier = new IdleNotifier();
        var counter = new AtomicInteger(0);
        notifier.register("INBOX", (s) -> counter.incrementAndGet());
        notifier.notifyExists("Drafts", 1);
        assertThat(counter.get()).isEqualTo(0);  // Listener for INBOX shouldn't get Drafts notification
    }

    @Test void listenerCount() {
        var notifier = new IdleNotifier();
        assertThat(notifier.listenerCount("INBOX")).isEqualTo(0);
        
        Consumer<String> l1 = (s) -> {};
        Consumer<String> l2 = (s) -> {};
        notifier.register("INBOX", l1);
        assertThat(notifier.listenerCount("INBOX")).isEqualTo(1);
        notifier.register("INBOX", l2);
        assertThat(notifier.listenerCount("INBOX")).isEqualTo(2);
    }

    @Test void clearListeners() {
        var notifier = new IdleNotifier();
        notifier.register("INBOX", (s) -> {});
        notifier.register("INBOX", (s) -> {});
        assertThat(notifier.listenerCount("INBOX")).isEqualTo(2);
        
        notifier.clearListeners("INBOX");
        assertThat(notifier.listenerCount("INBOX")).isEqualTo(0);
    }

    @Test void notifyGeneric() {
        var notifier = new IdleNotifier();
        var counter = new AtomicInteger(0);
        notifier.register("INBOX", (s) -> counter.incrementAndGet());
        notifier.notify("INBOX", "* 5 EXISTS");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test void multipleListenersSameMailbox() {
        var notifier = new IdleNotifier();
        var c1 = new AtomicInteger(0);
        var c2 = new AtomicInteger(0);
        notifier.register("INBOX", (s) -> c1.incrementAndGet());
        notifier.register("INBOX", (s) -> c2.incrementAndGet());
        
        notifier.notifyExists("INBOX", 3);
        assertThat(c1.get()).isEqualTo(1);
        assertThat(c2.get()).isEqualTo(1);
    }
}
