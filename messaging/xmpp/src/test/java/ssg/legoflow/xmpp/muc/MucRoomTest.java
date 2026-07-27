package ssg.legoflow.xmpp.muc;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MucRoom}.
 *
 * @since 1.0.0
 */
class MucRoomTest {

    private MucRoom room;
    private JID roomJid;

    @BeforeEach
    void setUp() {
        roomJid = JID.parse("testroom@conference.example.com");
        room = new MucRoom(roomJid);
    }

    @Test
    void testRoomCreation() {
        assertThat(room.getRoomJid()).isEqualTo(roomJid);
        assertThat(room.isJoined()).isFalse();
        assertThat(room.getLocalNick()).isNull();
        assertThat(room.getOccupants()).isEmpty();
        assertThat(room.getMessages()).isEmpty();
    }

    @Test
    void testJoinAndLeave() {
        room.markJoined("alice");
        assertThat(room.isJoined()).isTrue();
        assertThat(room.getLocalNick()).isEqualTo("alice");

        room.markLeft();
        assertThat(room.isJoined()).isFalse();
        assertThat(room.getLocalNick()).isNull();
    }

    @Test
    void testAddAndRemoveOccupant() {
        var occupant = new MucOccupant(
                roomJid.withResource("bob"), JID.parse("bob@example.com"),
                "bob", MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.MEMBER);
        room.addOccupant(occupant);

        assertThat(room.getOccupants()).hasSize(1);
        assertThat(room.getOccupant("bob")).isNotNull();
        assertThat(room.getOccupantCount()).isEqualTo(1);

        room.removeOccupant("bob");
        assertThat(room.getOccupants()).isEmpty();
    }

    @Test
    void testAddMessage() {
        var msg = new MucMessage("m1", roomJid.withResource("alice"), roomJid, "Hi!", Instant.now());
        room.addMessage(msg);

        assertThat(room.getMessages()).hasSize(1);
        assertThat(room.getMessageCount()).isEqualTo(1);
    }

    @Test
    void testChangeNick() {
        var occupant = new MucOccupant(
                roomJid.withResource("alice"), null,
                "alice", MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.NONE);
        room.addOccupant(occupant);
        room.markJoined("alice");

        boolean changed = room.changeNick("alice", "alice2");
        assertThat(changed).isTrue();
        assertThat(room.getOccupant("alice")).isNull();
        assertThat(room.getOccupant("alice2")).isNotNull();
        assertThat(room.getOccupant("alice2").nick()).isEqualTo("alice2");
        assertThat(room.getLocalNick()).isEqualTo("alice2");
    }

    @Test
    void testChangeNickNonexistent() {
        assertThat(room.changeNick("ghost", "phantom")).isFalse();
    }

    @Test
    void testChangeRole() {
        var occupant = new MucOccupant(
                roomJid.withResource("bob"), null,
                "bob", MucOccupant.Role.VISITOR, MucOccupant.Affiliation.NONE);
        room.addOccupant(occupant);

        boolean changed = room.changeRole("bob", MucOccupant.Role.MODERATOR);
        assertThat(changed).isTrue();
        assertThat(room.getOccupant("bob").role()).isEqualTo(MucOccupant.Role.MODERATOR);
    }

    @Test
    void testChangeAffiliation() {
        var occupant = new MucOccupant(
                roomJid.withResource("carol"), null,
                "carol", MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.NONE);
        room.addOccupant(occupant);

        boolean changed = room.changeAffiliation("carol", MucOccupant.Affiliation.ADMIN);
        assertThat(changed).isTrue();
        assertThat(room.getOccupant("carol").affiliation()).isEqualTo(MucOccupant.Affiliation.ADMIN);
    }

    @Test
    void testSubjectAndConfig() {
        room.setSubject("Topic of the day");
        assertThat(room.getSubject()).isEqualTo("Topic of the day");

        room.setMembersOnly(true);
        assertThat(room.isMembersOnly()).isTrue();

        room.setPersistent(true);
        assertThat(room.isPersistent()).isTrue();
    }
}
