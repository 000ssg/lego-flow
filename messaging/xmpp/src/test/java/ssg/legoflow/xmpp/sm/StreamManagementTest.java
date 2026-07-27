package ssg.legoflow.xmpp.sm;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.MessageStanza;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StreamManagement}.
 *
 * @since 1.0.0
 */
class StreamManagementTest {

    private StreamManagement sm;

    @BeforeEach
    void setUp() {
        sm = new StreamManagement();
    }

    @Test
    void testInitialState() {
        assertThat(sm.isEnabled()).isFalse();
        assertThat(sm.isResumable()).isFalse();
        assertThat(sm.getOutboundCount()).isZero();
        assertThat(sm.getInboundCount()).isZero();
        assertThat(sm.getUnackedCount()).isZero();
    }

    @Test
    void testEnable() {
        String xml = sm.enable(false);

        assertThat(sm.isEnabled()).isTrue();
        assertThat(sm.isResumable()).isFalse();
        assertThat(xml).contains("xmlns=\"" + StreamManagement.NAMESPACE + "\"");
        assertThat(xml).doesNotContain("resume");
    }

    @Test
    void testEnableWithResume() {
        String xml = sm.enable(true);

        assertThat(sm.isResumable()).isTrue();
        assertThat(xml).contains("resume=\"true\"");
    }

    @Test
    void testHandleEnabled() {
        sm.enable(true);
        sm.handleEnabled("sm-session-1", true);

        assertThat(sm.getSessionId()).isEqualTo("sm-session-1");
        assertThat(sm.isResumable()).isTrue();
    }

    @Test
    void testTrackOutbound() {
        sm.enable(false);
        var msg = MessageStanza.chat("m1", JID.parse("a@example.com"),
                JID.parse("b@example.com"), "hello");
        sm.trackOutbound(msg);

        assertThat(sm.getOutboundCount()).isEqualTo(1);
        assertThat(sm.getUnackedCount()).isEqualTo(1);
        assertThat(sm.getUnackedStanzas()).hasSize(1);
    }

    @Test
    void testTrackOutboundWhenDisabled() {
        var msg = MessageStanza.chat("m1", JID.parse("a@example.com"),
                JID.parse("b@example.com"), "hello");
        sm.trackOutbound(msg);

        // Should be a no-op when disabled
        assertThat(sm.getOutboundCount()).isZero();
        assertThat(sm.getUnackedCount()).isZero();
    }

    @Test
    void testTrackInbound() {
        sm.enable(false);
        var msg = MessageStanza.chat("m1", JID.parse("a@example.com"),
                JID.parse("b@example.com"), "hello");
        sm.trackInbound(msg);

        assertThat(sm.getInboundCount()).isEqualTo(1);
    }

    @Test
    void testRequestAck() {
        String xml = sm.requestAck();
        assertThat(xml).isEqualTo("<r xmlns=\"" + StreamManagement.NAMESPACE + "\"/>");
    }

    @Test
    void testGenerateAck() {
        sm.enable(false);
        var msg = MessageStanza.chat("m1", JID.parse("a@example.com"),
                JID.parse("b@example.com"), "hello");
        sm.trackInbound(msg);
        sm.trackInbound(msg);

        String xml = sm.generateAck();
        assertThat(xml).contains("h=\"2\"");
    }

    @Test
    void testProcessAck() {
        sm.enable(false);
        for (int i = 0; i < 5; i++) {
            sm.trackOutbound(MessageStanza.chat("m" + i,
                    JID.parse("a@example.com"), JID.parse("b@example.com"), "msg " + i));
        }

        assertThat(sm.getUnackedCount()).isEqualTo(5);

        int removed = sm.processAck(3);
        assertThat(removed).isEqualTo(3);
        assertThat(sm.getUnackedCount()).isEqualTo(2);
        assertThat(sm.getLastAckedOutbound()).isEqualTo(3);
    }

    @Test
    void testProcessAckAll() {
        sm.enable(false);
        for (int i = 0; i < 3; i++) {
            sm.trackOutbound(MessageStanza.chat("m" + i,
                    JID.parse("a@example.com"), JID.parse("b@example.com"), "msg"));
        }

        int removed = sm.processAck(3);
        assertThat(removed).isEqualTo(3);
        assertThat(sm.getUnackedCount()).isZero();
    }

    @Test
    void testProcessAckBackwards() {
        sm.enable(false);
        sm.trackOutbound(MessageStanza.chat("m1",
                JID.parse("a@example.com"), JID.parse("b@example.com"), "msg"));
        sm.processAck(1);

        // Ack with h < lastAcked should be ignored
        int removed = sm.processAck(0);
        assertThat(removed).isZero();
    }

    @Test
    void testResume() {
        sm.enable(true);
        sm.handleEnabled("sm-session-1", true);

        // Simulate some traffic
        for (int i = 0; i < 3; i++) {
            sm.trackOutbound(MessageStanza.chat("m" + i,
                    JID.parse("a@example.com"), JID.parse("b@example.com"), "msg"));
        }
        sm.trackInbound(MessageStanza.chat("r1",
                JID.parse("b@example.com"), JID.parse("a@example.com"), "reply"));

        String resumeXml = sm.resume();
        assertThat(resumeXml).isNotNull();
        assertThat(resumeXml).contains("previd=\"sm-session-1\"");
        assertThat(resumeXml).contains("h=\"1\"");
    }

    @Test
    void testResumeNotAvailable() {
        sm.enable(false);
        String xml = sm.resume();
        assertThat(xml).isNull();
    }

    @Test
    void testHandleResumed() {
        sm.enable(true);
        sm.handleEnabled("sm-session-1", true);

        for (int i = 0; i < 5; i++) {
            sm.trackOutbound(MessageStanza.chat("m" + i,
                    JID.parse("a@example.com"), JID.parse("b@example.com"), "msg"));
        }

        // Server acked 3 of our 5 stanzas
        int removed = sm.handleResumed(3);
        assertThat(removed).isEqualTo(3);
        assertThat(sm.getUnackedCount()).isEqualTo(2);

        // Remaining stanzas should be available for resend
        assertThat(sm.getUnackedStanzas()).hasSize(2);
    }

    @Test
    void testDisable() {
        sm.enable(true);
        sm.handleEnabled("sm-1", true);
        sm.trackOutbound(MessageStanza.chat("m1",
                JID.parse("a@example.com"), JID.parse("b@example.com"), "msg"));

        sm.disable();

        assertThat(sm.isEnabled()).isFalse();
        assertThat(sm.isResumable()).isFalse();
        assertThat(sm.getSessionId()).isNull();
        assertThat(sm.getUnackedCount()).isZero();
        assertThat(sm.getOutboundCount()).isZero();
        assertThat(sm.getInboundCount()).isZero();
    }

    @Test
    void testMultipleOutboundThenPartialAck() {
        sm.enable(false);
        for (int i = 1; i <= 10; i++) {
            sm.trackOutbound(MessageStanza.chat("m" + i,
                    JID.parse("a@example.com"), JID.parse("b@example.com"), "msg " + i));
        }

        assertThat(sm.getOutboundCount()).isEqualTo(10);
        assertThat(sm.getUnackedCount()).isEqualTo(10);

        sm.processAck(4);
        assertThat(sm.getUnackedCount()).isEqualTo(6);

        sm.processAck(7);
        assertThat(sm.getUnackedCount()).isEqualTo(3);

        sm.processAck(10);
        assertThat(sm.getUnackedCount()).isZero();
    }
}
