package ssg.legoflow.xmpp.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MessageStanza}.
 *
 * @since 1.0.0
 */
class MessageStanzaTest {

    private final JID alice = JID.parse("alice@example.com/desktop");
    private final JID bob = JID.parse("bob@example.com/mobile");

    @Test
    void testChatMessage() {
        var msg = MessageStanza.chat("msg-1", alice, bob, "Hello Bob!");
        assertThat(msg.id()).isEqualTo("msg-1");
        assertThat(msg.from()).isEqualTo(alice);
        assertThat(msg.to()).isEqualTo(bob);
        assertThat(msg.messageType()).isEqualTo(MessageStanza.MessageType.CHAT);
        assertThat(msg.body()).isEqualTo("Hello Bob!");
    }

    @Test
    void testStanzaType() {
        var msg = MessageStanza.chat("msg-1", alice, bob, "test");
        assertThat(msg.type()).isEqualTo(StanzaType.MESSAGE);
    }

    @Test
    void testMessageTypes() {
        for (var type : MessageStanza.MessageType.values()) {
            var msg = new MessageStanza("id", alice, bob, type, "body", null, null, List.of());
            assertThat(msg.messageType()).isEqualTo(type);
        }
    }

    @Test
    void testMessageWithSubjectAndThread() {
        var msg = new MessageStanza("msg-1", alice, bob,
                MessageStanza.MessageType.NORMAL, "body", "subject", "thread-1", List.of());
        assertThat(msg.subject()).isEqualTo("subject");
        assertThat(msg.thread()).isEqualTo("thread-1");
    }

    @Test
    void testToXml() {
        var msg = MessageStanza.chat("msg-1", alice, bob, "Hello!");
        var xml = msg.toXml();
        assertThat(xml).contains("<message");
        assertThat(xml).contains("id=\"msg-1\"");
        assertThat(xml).contains("<body>Hello!</body>");
        assertThat(xml).contains("type=\"chat\"");
    }

    @Test
    void testExtensionsAreImmutable() {
        var msg = MessageStanza.chat("msg-1", alice, bob, "test");
        assertThat(msg.extensions()).isEmpty();
        assertThatThrownBy(() -> msg.extensions().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
