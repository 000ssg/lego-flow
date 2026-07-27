package ssg.legoflow.xmpp.presence;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.PresenceStanza;
import ssg.legoflow.xmpp.core.PresenceStanza.PresenceShow;
import ssg.legoflow.xmpp.core.PresenceStanza.PresenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PresenceManager}.
 *
 * @since 1.0.0
 */
class PresenceManagerTest {

    private PresenceManager manager;
    private final JID localJid = JID.parse("user@example.com/desktop");
    private final JID contactJid = JID.parse("friend@example.com/mobile");

    @BeforeEach
    void setUp() {
        manager = new PresenceManager(localJid);
    }

    @Test
    void testSendAvailable() {
        var pres = manager.sendAvailable();
        assertThat(pres.presenceType()).isEqualTo(PresenceType.AVAILABLE);
        assertThat(manager.getCurrentPresence()).isEqualTo(pres);
    }

    @Test
    void testSendUnavailable() {
        var pres = manager.sendUnavailable();
        assertThat(pres.presenceType()).isEqualTo(PresenceType.UNAVAILABLE);
    }

    @Test
    void testSendPresenceWithShow() {
        var pres = manager.sendPresence(PresenceShow.DND, "Busy");
        assertThat(pres.show()).isEqualTo(PresenceShow.DND);
        assertThat(pres.status()).isEqualTo("Busy");
    }

    @Test
    void testSubscribe() {
        var pres = manager.subscribe(contactJid);
        assertThat(pres.presenceType()).isEqualTo(PresenceType.SUBSCRIBE);
        assertThat(pres.to()).isEqualTo(contactJid);
    }

    @Test
    void testApproveSubscription() {
        var pres = manager.approveSubscription(contactJid);
        assertThat(pres.presenceType()).isEqualTo(PresenceType.SUBSCRIBED);
    }

    @Test
    void testDenySubscription() {
        var pres = manager.denySubscription(contactJid);
        assertThat(pres.presenceType()).isEqualTo(PresenceType.UNSUBSCRIBED);
    }

    @Test
    void testHandlePresence() {
        var incoming = new PresenceStanza(UUID.randomUUID().toString(),
                contactJid, localJid, PresenceType.AVAILABLE,
                PresenceShow.CHAT, "Let's talk", 0, List.of());
        manager.handlePresence(incoming);
        var stored = manager.getPresence(contactJid);
        assertThat(stored).isNotNull();
        assertThat(stored.show()).isEqualTo(PresenceShow.CHAT);
    }

    @Test
    void testPresenceListener() {
        var received = new ArrayList<PresenceStanza>();
        manager.addPresenceListener((jid, pres) -> received.add(pres));
        var incoming = new PresenceStanza(UUID.randomUUID().toString(),
                contactJid, localJid, PresenceType.AVAILABLE,
                PresenceShow.AWAY, "BRB", 0, List.of());
        manager.handlePresence(incoming);
        assertThat(received).hasSize(1);
        assertThat(received.getFirst().show()).isEqualTo(PresenceShow.AWAY);
    }
}
