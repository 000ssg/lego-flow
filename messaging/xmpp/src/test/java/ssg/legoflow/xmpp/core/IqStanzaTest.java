package ssg.legoflow.xmpp.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link IqStanza}.
 *
 * @since 1.0.0
 */
class IqStanzaTest {

    private final JID from = JID.parse("alice@example.com/desktop");
    private final JID to = JID.parse("bob@example.com/mobile");

    @Test
    void testGetIq() {
        var iq = IqStanza.get("iq-1", from, to, null);
        assertThat(iq.iqType()).isEqualTo(IqStanza.IqType.GET);
        assertThat(iq.id()).isEqualTo("iq-1");
    }

    @Test
    void testSetIq() {
        var iq = IqStanza.set("iq-2", from, to, null);
        assertThat(iq.iqType()).isEqualTo(IqStanza.IqType.SET);
    }

    @Test
    void testResultIq() {
        var iq = IqStanza.result("iq-3", from, to, null);
        assertThat(iq.iqType()).isEqualTo(IqStanza.IqType.RESULT);
    }

    @Test
    void testErrorIq() {
        var iq = new IqStanza("iq-4", from, to, IqStanza.IqType.ERROR, null, null);
        assertThat(iq.iqType()).isEqualTo(IqStanza.IqType.ERROR);
    }

    @Test
    void testStanzaType() {
        var iq = IqStanza.get("iq-1", from, to, null);
        assertThat(iq.type()).isEqualTo(StanzaType.IQ);
    }

    @Test
    void testToXml() {
        var iq = IqStanza.get("iq-1", from, to, null);
        var xml = iq.toXml();
        assertThat(xml).contains("<iq");
        assertThat(xml).contains("id=\"iq-1\"");
        assertThat(xml).contains("type=\"get\"");
    }
}
