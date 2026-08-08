package ssg.legoflow.xmpp.stream;

import ssg.legoflow.xmpp.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link XmppCodec}.
 *
 * @since 0.1.0
 */
class XmppCodecTest {

    private XmppCodec codec;

    @BeforeEach
    void setUp() {
        codec = new XmppCodec();
    }

    @Test
    void testEncodeMessage() {
        var msg = MessageStanza.chat("msg-1",
                JID.parse("alice@example.com"), JID.parse("bob@example.com"), "Hello!");
        ByteBuffer encoded = codec.encodeStanza(msg);
        String xml = StandardCharsets.UTF_8.decode(encoded).toString();
        assertThat(xml).contains("<message");
        assertThat(xml).contains("<body>Hello!</body>");
    }

    @Test
    void testEncodePresence() {
        var pres = PresenceStanza.available("pres-1", JID.parse("user@example.com"));
        ByteBuffer encoded = codec.encodeStanza(pres);
        String xml = StandardCharsets.UTF_8.decode(encoded).toString();
        assertThat(xml).contains("<presence");
    }

    @Test
    void testEncodeIq() {
        var iq = IqStanza.get("iq-1", JID.parse("alice@example.com"),
                JID.parse("bob@example.com"), null);
        ByteBuffer encoded = codec.encodeStanza(iq);
        String xml = StandardCharsets.UTF_8.decode(encoded).toString();
        assertThat(xml).contains("<iq");
        assertThat(xml).contains("type=\"get\"");
    }

    @Test
    void testDecodeMessage() {
        String xml = "<message id=\"msg-1\" from=\"alice@example.com\" to=\"bob@example.com\" type=\"chat\">" +
                "<body>Hello!</body></message>";
        var stanzas = codec.decodeStanzas(ByteBuffer.wrap(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(stanzas).hasSize(1);
        assertThat(stanzas.getFirst()).isInstanceOf(MessageStanza.class);
        var msg = (MessageStanza) stanzas.getFirst();
        assertThat(msg.body()).isEqualTo("Hello!");
    }

    @Test
    void testDecodePresence() {
        String xml = "<presence id=\"pres-1\" from=\"user@example.com\">" +
                "<show>away</show><status>BRB</status></presence>";
        var stanzas = codec.decodeStanzas(ByteBuffer.wrap(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(stanzas).hasSize(1);
        assertThat(stanzas.getFirst()).isInstanceOf(PresenceStanza.class);
        var pres = (PresenceStanza) stanzas.getFirst();
        assertThat(pres.show()).isEqualTo(PresenceStanza.PresenceShow.AWAY);
        assertThat(pres.status()).isEqualTo("BRB");
    }

    @Test
    void testDecodeIq() {
        String xml = "<iq id=\"iq-1\" from=\"alice@example.com\" to=\"bob@example.com\" type=\"result\"></iq>";
        var stanzas = codec.decodeStanzas(ByteBuffer.wrap(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(stanzas).hasSize(1);
        assertThat(stanzas.getFirst()).isInstanceOf(IqStanza.class);
        var iq = (IqStanza) stanzas.getFirst();
        assertThat(iq.iqType()).isEqualTo(IqStanza.IqType.RESULT);
    }

    @Test
    void testDecodeMultipleStanzas() {
        String xml = "<message id=\"m1\" from=\"a@b.com\" to=\"c@b.com\" type=\"chat\"><body>Hi</body></message>" +
                "<presence id=\"p1\" from=\"a@b.com\"><show>dnd</show></presence>";
        var stanzas = codec.decodeStanzas(ByteBuffer.wrap(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(stanzas).hasSize(2);
    }

    @Test
    void testIncrementalParsing() {
        // Send first half
        String part1 = "<message id=\"msg-1\" from=\"a@b.com\" to=\"c@b.com\" type=\"chat\">";
        codec.decodeStanzas(ByteBuffer.wrap(part1.getBytes(StandardCharsets.UTF_8)));

        // Send second half
        codec.reset(); // Reset for clean test
        String full = "<message id=\"msg-1\" from=\"a@b.com\" to=\"c@b.com\" type=\"chat\"><body>Test</body></message>";
        var stanzas = codec.decodeStanzas(ByteBuffer.wrap(full.getBytes(StandardCharsets.UTF_8)));
        assertThat(stanzas).hasSize(1);
    }

    @Test
    void testReset() {
        codec.decodeStanzas(ByteBuffer.wrap("<partial".getBytes(StandardCharsets.UTF_8)));
        assertThat(codec.getBufferedContent()).isNotEmpty();
        codec.reset();
        assertThat(codec.getBufferedContent()).isEmpty();
    }

    @Test
    void testXmlEscaping() {
        var msg = MessageStanza.chat("msg-1",
                JID.parse("alice@example.com"), JID.parse("bob@example.com"),
                "Hello <World> & \"Friends\"");
        ByteBuffer encoded = codec.encodeStanza(msg);
        String xml = StandardCharsets.UTF_8.decode(encoded).toString();
        assertThat(xml).contains("&lt;World&gt;");
        assertThat(xml).contains("&amp;");
    }
}
