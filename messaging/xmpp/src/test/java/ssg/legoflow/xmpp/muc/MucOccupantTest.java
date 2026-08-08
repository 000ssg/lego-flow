package ssg.legoflow.xmpp.muc;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MucOccupant}.
 *
 * @since 0.1.0
 */
class MucOccupantTest {

    @Test
    void testCreateOccupant() {
        var roomJid = JID.parse("room@conference.example.com/alice");
        var realJid = JID.parse("alice@example.com");
        var occupant = new MucOccupant(roomJid, realJid, "alice",
                MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.MEMBER);

        assertThat(occupant.roomJid()).isEqualTo(roomJid);
        assertThat(occupant.realJid()).isEqualTo(realJid);
        assertThat(occupant.nick()).isEqualTo("alice");
        assertThat(occupant.role()).isEqualTo(MucOccupant.Role.PARTICIPANT);
        assertThat(occupant.affiliation()).isEqualTo(MucOccupant.Affiliation.MEMBER);
    }

    @Test
    void testOccupantWithNullRealJid() {
        var roomJid = JID.parse("room@conference.example.com/bob");
        var occupant = new MucOccupant(roomJid, null, "bob",
                MucOccupant.Role.VISITOR, MucOccupant.Affiliation.NONE);

        assertThat(occupant.realJid()).isNull();
        assertThat(occupant.nick()).isEqualTo("bob");
    }

    @Test
    void testToXml() {
        var roomJid = JID.parse("room@conference.example.com/alice");
        var realJid = JID.parse("alice@example.com");
        var occupant = new MucOccupant(roomJid, realJid, "alice",
                MucOccupant.Role.MODERATOR, MucOccupant.Affiliation.OWNER);

        String xml = occupant.toXml();
        assertThat(xml).contains("jid=\"alice@example.com\"");
        assertThat(xml).contains("nick=\"alice\"");
        assertThat(xml).contains("role=\"moderator\"");
        assertThat(xml).contains("affiliation=\"owner\"");
    }

    @Test
    void testToXmlWithoutRealJid() {
        var roomJid = JID.parse("room@conference.example.com/anon");
        var occupant = new MucOccupant(roomJid, null, "anon",
                MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.NONE);

        String xml = occupant.toXml();
        assertThat(xml).doesNotContain("jid=");
        assertThat(xml).contains("nick=\"anon\"");
    }

    @Test
    void testNullRoomJidThrows() {
        assertThatThrownBy(() -> new MucOccupant(null, null, "nick",
                MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.NONE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullNickThrows() {
        var roomJid = JID.parse("room@conference.example.com/x");
        assertThatThrownBy(() -> new MucOccupant(roomJid, null, null,
                MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.NONE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAllRoles() {
        for (var role : MucOccupant.Role.values()) {
            assertThat(role).isNotNull();
        }
        assertThat(MucOccupant.Role.values()).hasSize(4);
    }

    @Test
    void testAllAffiliations() {
        for (var aff : MucOccupant.Affiliation.values()) {
            assertThat(aff).isNotNull();
        }
        assertThat(MucOccupant.Affiliation.values()).hasSize(5);
    }
}
