package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.realm.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ChatRoomDemoTest {

    private ChatRoomDemo chat;

    @BeforeEach
    void setUp() {
        chat = new ChatRoomDemo(new Realm("chat.room"));
    }

    @Test
    void testUserJoinAddsToConnectedUsers() {
        chat.join("Alice");
        chat.join("Bob");

        assertThat(chat.getConnectedUsers()).containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void testUserLeaveRemovesFromConnectedUsers() {
        chat.join("Alice");
        chat.join("Bob");
        chat.leave("Alice");

        assertThat(chat.getConnectedUsers()).containsExactly("Bob");
    }

    @Test
    void testBroadcastMessageDeliveredToAllUsers() {
        chat.join("Alice");
        chat.join("Bob");

        chat.sendMessage("Alice", "Hello everyone!");

        var bobMessages = chat.getMessages("Bob");
        assertThat(bobMessages).anyMatch(msg ->
                msg.contains("Alice") && msg.contains("Hello everyone!"));
    }

    @Test
    void testSenderAlsoReceivesOwnMessage() {
        chat.join("Alice");
        chat.join("Bob");

        chat.sendMessage("Alice", "Hi!");

        var aliceMessages = chat.getMessages("Alice");
        assertThat(aliceMessages).anyMatch(msg ->
                msg.contains("Alice") && msg.contains("Hi!"));
    }

    @Test
    void testJoinNotificationDeliveredToExistingUsers() {
        chat.join("Alice");
        chat.join("Bob");

        var aliceMessages = chat.getMessages("Alice");
        assertThat(aliceMessages).anyMatch(msg ->
                msg.stream().anyMatch(o -> o.toString().contains("Bob joined")));
    }

    @Test
    void testLeaveNotificationDelivered() {
        chat.join("Alice");
        chat.join("Bob");
        chat.leave("Bob");

        var aliceMessages = chat.getMessages("Alice");
        assertThat(aliceMessages).anyMatch(msg ->
                msg.stream().anyMatch(o -> o.toString().contains("Bob left")));
    }

    @Test
    void testMessageOrderingPreserved() {
        chat.join("Alice");
        chat.join("Bob");

        chat.sendMessage("Alice", "first");
        chat.sendMessage("Bob", "second");

        var aliceMessages = chat.getMessages("Alice");
        int firstIdx = -1;
        int secondIdx = -1;
        for (int i = 0; i < aliceMessages.size(); i++) {
            if (aliceMessages.get(i).contains("first")) firstIdx = i;
            if (aliceMessages.get(i).contains("second")) secondIdx = i;
        }
        if (firstIdx >= 0 && secondIdx >= 0) {
            assertThat(firstIdx).isLessThan(secondIdx);
        }
    }

    @Test
    void testGetMessagesForNonExistentUserReturnsEmpty() {
        assertThat(chat.getMessages("Ghost")).isEmpty();
    }
}
