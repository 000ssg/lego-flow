package ssg.legoflow.xmpp.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PresenceStanza}.
 *
 * @since 1.0.0
 */
class PresenceStanzaTest {

    private final JID user = JID.parse("user@example.com/desktop");

    @Test
    void testAvailablePresence() {
        var presence = PresenceStanza.available("pres-1", user);
        assertThat(presence.presenceType()).isEqualTo(PresenceStanza.PresenceType.AVAILABLE);
        assertThat(presence.id()).isEqualTo("pres-1");
        assertThat(presence.from()).isEqualTo(user);
    }

    @Test
    void testUnavailablePresence() {
        var presence = PresenceStanza.unavailable("pres-2", user);
        assertThat(presence.presenceType()).isEqualTo(PresenceStanza.PresenceType.UNAVAILABLE);
    }

    @Test
    void testPresenceType() {
        var presence = PresenceStanza.available("pres-1", user);
        assertThat(presence.type()).isEqualTo(StanzaType.PRESENCE);
    }

    @Test
    void testPresenceShow() {
        var presence = new PresenceStanza("pres-1", user, null,
                PresenceStanza.PresenceType.AVAILABLE, PresenceStanza.PresenceShow.DND,
                "Do not disturb", 5, List.of());
        assertThat(presence.show()).isEqualTo(PresenceStanza.PresenceShow.DND);
        assertThat(presence.status()).isEqualTo("Do not disturb");
        assertThat(presence.priority()).isEqualTo(5);
    }

    @Test
    void testPriorityBounds() {
        assertThatThrownBy(() -> new PresenceStanza("p-1", user, null,
                PresenceStanza.PresenceType.AVAILABLE, null, null, 200, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToXml() {
        var presence = new PresenceStanza("pres-1", user, null,
                PresenceStanza.PresenceType.AVAILABLE, PresenceStanza.PresenceShow.AWAY,
                "Away", 0, List.of());
        var xml = presence.toXml();
        assertThat(xml).contains("<presence");
        assertThat(xml).contains("<show>away</show>");
        assertThat(xml).contains("<status>Away</status>");
    }
}
