package ssg.legoflow.xmpp.demo;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.PresenceStanza.PresenceShow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PresenceDemo}.
 *
 * @since 0.1.0
 */
class PresenceDemoTest {

    private PresenceDemo demo;

    @BeforeEach
    void setUp() {
        demo = new PresenceDemo();
        demo.setup("example.com");
    }

    @AfterEach
    void tearDown() {
        demo.shutdown();
    }

    @Test
    void testClientSetup() {
        assertThat(demo.getClient().isConnected()).isTrue();
        assertThat(demo.getClient().isAuthenticated()).isTrue();
    }

    @Test
    void testPresenceStates() {
        demo.demonstratePresenceStates();
        var sent = demo.getClient().getPresenceManager().getSentPresences();
        assertThat(sent).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void testSubscription() {
        var contact = JID.parse("friend@example.com");
        demo.demonstrateSubscription(contact);
        assertThat(demo.getReceivedPresences()).isNotEmpty();
    }

    @Test
    void testReceivePresence() {
        var contact = JID.parse("friend@example.com/mobile");
        demo.receivePresence(contact, PresenceShow.AWAY, "AFK");
        var stored = demo.getClient().getPresenceManager().getPresence(contact);
        assertThat(stored).isNotNull();
        assertThat(stored.show()).isEqualTo(PresenceShow.AWAY);
    }
}
