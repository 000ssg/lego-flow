package ssg.legoflow.xmpp.muc;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link MucRoomManager}.
 *
 * @since 0.1.0
 */
class MucRoomManagerTest {

    private MucRoomManager manager;
    private JID localJid;
    private JID roomJid;

    @BeforeEach
    void setUp() {
        localJid = JID.parse("alice@example.com/desktop");
        manager = new MucRoomManager(localJid);
        roomJid = JID.parse("testroom@conference.example.com");
    }

    @Test
    void testJoinRoom() {
        var room = manager.join(roomJid, "alice");

        assertThat(room).isNotNull();
        assertThat(room.isJoined()).isTrue();
        assertThat(room.getLocalNick()).isEqualTo("alice");
        assertThat(room.getOccupants()).hasSize(1);
        assertThat(room.getOccupant("alice").role()).isEqualTo(MucOccupant.Role.PARTICIPANT);
    }

    @Test
    void testLeaveRoom() {
        manager.join(roomJid, "alice");
        manager.leave(roomJid);

        var room = manager.getRoom(roomJid);
        assertThat(room.isJoined()).isFalse();
    }

    @Test
    void testLeaveWithoutJoin() {
        manager.leave(roomJid); // should not throw
    }

    @Test
    void testSendMessage() {
        manager.join(roomJid, "alice");
        var msg = manager.sendMessage(roomJid, "Hello room!");

        assertThat(msg).isNotNull();
        assertThat(msg.body()).isEqualTo("Hello room!");
        assertThat(msg.roomJid()).isEqualTo(roomJid);

        var room = manager.getRoom(roomJid);
        assertThat(room.getMessages()).hasSize(1);
    }

    @Test
    void testSendMessageWhenNotJoined() {
        var msg = manager.sendMessage(roomJid, "Hello");
        assertThat(msg).isNull();
    }

    @Test
    void testMessageListener() {
        var received = new ArrayList<MucMessage>();
        manager.addMessageListener(received::add);

        manager.join(roomJid, "alice");
        manager.sendMessage(roomJid, "Hello!");

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().body()).isEqualTo("Hello!");
    }

    @Test
    void testHandleIncomingMessage() {
        manager.join(roomJid, "alice");
        var received = new ArrayList<MucMessage>();
        manager.addMessageListener(received::add);

        var incoming = new MucMessage("m1",
                roomJid.withResource("bob"), roomJid, "From Bob", Instant.now());
        manager.handleMessage(incoming);

        assertThat(received).hasSize(1);
        var room = manager.getRoom(roomJid);
        assertThat(room.getMessages()).hasSize(1);
    }

    @Test
    void testOccupantJoinAndLeave() {
        manager.join(roomJid, "alice");

        var bobOccupant = new MucOccupant(
                roomJid.withResource("bob"), JID.parse("bob@example.com"),
                "bob", MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.MEMBER);

        var occupantEvents = new ArrayList<MucOccupant>();
        manager.addOccupantListener(occupantEvents::add);

        manager.handleOccupantJoin(roomJid, bobOccupant);
        assertThat(occupantEvents).hasSize(1);

        var room = manager.getRoom(roomJid);
        assertThat(room.getOccupants()).hasSize(2); // alice + bob

        manager.handleOccupantLeave(roomJid, "bob");
        assertThat(room.getOccupants()).hasSize(1);
    }

    @Test
    void testChangeNick() {
        manager.join(roomJid, "alice");
        boolean changed = manager.changeNick(roomJid, "alice2");

        assertThat(changed).isTrue();
        var room = manager.getRoom(roomJid);
        assertThat(room.getLocalNick()).isEqualTo("alice2");
    }

    @Test
    void testChangeNickNotJoined() {
        assertThat(manager.changeNick(roomJid, "nope")).isFalse();
    }

    @Test
    void testSetRole() {
        manager.join(roomJid, "alice");

        var bob = new MucOccupant(roomJid.withResource("bob"), null, "bob",
                MucOccupant.Role.VISITOR, MucOccupant.Affiliation.NONE);
        manager.handleOccupantJoin(roomJid, bob);

        boolean changed = manager.setRole(roomJid, "bob", MucOccupant.Role.PARTICIPANT);
        assertThat(changed).isTrue();

        var room = manager.getRoom(roomJid);
        assertThat(room.getOccupant("bob").role()).isEqualTo(MucOccupant.Role.PARTICIPANT);
    }

    @Test
    void testSetAffiliation() {
        manager.join(roomJid, "alice");

        var bob = new MucOccupant(roomJid.withResource("bob"), null, "bob",
                MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.NONE);
        manager.handleOccupantJoin(roomJid, bob);

        boolean changed = manager.setAffiliation(roomJid, "bob", MucOccupant.Affiliation.ADMIN);
        assertThat(changed).isTrue();

        var room = manager.getRoom(roomJid);
        assertThat(room.getOccupant("bob").affiliation()).isEqualTo(MucOccupant.Affiliation.ADMIN);
    }

    @Test
    void testGetRooms() {
        manager.join(roomJid, "alice");
        var room2 = JID.parse("room2@conference.example.com");
        manager.join(room2, "alice");

        assertThat(manager.getRooms()).hasSize(2);
    }

    @Test
    void testGenerateJoinPresenceXml() {
        String xml = manager.generateJoinPresenceXml(roomJid, "alice");
        assertThat(xml).contains("to=\"testroom@conference.example.com/alice\"");
        assertThat(xml).contains(MucRoomManager.NAMESPACE);
    }

    @Test
    void testGenerateLeavePresenceXml() {
        String xml = manager.generateLeavePresenceXml(roomJid, "alice");
        assertThat(xml).contains("type=\"unavailable\"");
        assertThat(xml).contains("to=\"testroom@conference.example.com/alice\"");
    }
}
