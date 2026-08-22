package ssg.legoflow.messaging.nats.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link InboxManager}.
 */
class InboxManagerTest {

    @Test
    void testNewInboxStartsWithPrefix() {
        var mgr = new InboxManager();
        String inbox = mgr.newInbox();
        assertThat(inbox).startsWith("_INBOX.");
    }

    @Test
    void testNewInboxUnique() {
        var mgr = new InboxManager();
        String inbox1 = mgr.newInbox();
        String inbox2 = mgr.newInbox();
        assertThat(inbox1).isNotEqualTo(inbox2);
    }

    @Test
    void testCustomPrefix() {
        var mgr = new InboxManager("myprefix");
        String inbox = mgr.newInbox();
        assertThat(inbox).startsWith("_INBOX.myprefix.");
    }

    @Test
    void testPrefixAccessor() {
        var mgr = new InboxManager("test");
        assertThat(mgr.prefix()).isEqualTo("_INBOX.test.");
    }

    @Test
    void testSequentialInboxes() {
        var mgr = new InboxManager("seq");
        String inbox1 = mgr.newInbox();
        String inbox2 = mgr.newInbox();
        assertThat(inbox1).endsWith(".1");
        assertThat(inbox2).endsWith(".2");
    }
}
