package ssg.legoflow.xmpp.muc;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link MucMessage}.
 *
 * @since 0.1.0
 */
class MucMessageTest {

    @Test
    void testCreateMessage() {
        var from = JID.parse("room@conference.example.com/alice");
        var room = JID.parse("room@conference.example.com");
        var msg = new MucMessage("msg-1", from, room, "Hello room!", Instant.now());

        assertThat(msg.id()).isEqualTo("msg-1");
        assertThat(msg.from()).isEqualTo(from);
        assertThat(msg.roomJid()).isEqualTo(room);
        assertThat(msg.body()).isEqualTo("Hello room!");
        assertThat(msg.timestamp()).isNotNull();
    }

    @Test
    void testToXml() {
        var from = JID.parse("room@conference.example.com/alice");
        var room = JID.parse("room@conference.example.com");
        var msg = new MucMessage("msg-1", from, room, "Hello!", Instant.now());

        String xml = msg.toXml();
        assertThat(xml).contains("type=\"groupchat\"");
        assertThat(xml).contains("to=\"room@conference.example.com\"");
        assertThat(xml).contains("<body>Hello!</body>");
    }

    @Test
    void testToXmlEscapesBody() {
        var room = JID.parse("room@conference.example.com");
        var msg = new MucMessage("msg-2", null, room, "a < b & c > d", Instant.now());

        String xml = msg.toXml();
        assertThat(xml).contains("a &lt; b &amp; c &gt; d");
    }

    @Test
    void testNullBodyThrows() {
        var room = JID.parse("room@conference.example.com");
        assertThatThrownBy(() -> new MucMessage("msg-1", null, room, null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullRoomJidThrows() {
        assertThatThrownBy(() -> new MucMessage("msg-1", null, null, "hello", Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
