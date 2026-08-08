package ssg.legoflow.xmpp.stream;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link XmppStream}.
 *
 * @since 0.1.0
 */
class XmppStreamTest {

    private XmppStream stream;
    private XmppCodec codec;

    @BeforeEach
    void setUp() {
        codec = new XmppCodec();
        stream = new XmppStream(codec);
    }

    @Test
    void testInitialState() {
        assertThat(stream.getState()).isEqualTo(XmppStreamState.INITIAL);
    }

    @Test
    void testOpenStream() {
        stream.open("example.com").join();
        assertThat(stream.getState()).isEqualTo(XmppStreamState.NEGOTIATING);
        assertThat(stream.getRemoteJid().domainpart()).isEqualTo("example.com");
    }

    @Test
    void testCloseStream() {
        stream.open("example.com").join();
        stream.markAuthenticated();
        stream.markBound(JID.parse("user@example.com/res"));
        stream.markActive();
        stream.closeStream().join();
        assertThat(stream.getState()).isEqualTo(XmppStreamState.CLOSED);
    }

    @Test
    void testStateTransitions() {
        stream.open("example.com").join();
        stream.markAuthenticated();
        assertThat(stream.getState()).isEqualTo(XmppStreamState.AUTHENTICATED);

        stream.markBound(JID.parse("user@example.com/res"));
        assertThat(stream.getState()).isEqualTo(XmppStreamState.BOUND);

        stream.markActive();
        assertThat(stream.getState()).isEqualTo(XmppStreamState.ACTIVE);
    }

    @Test
    void testNegotiateFeatures() {
        stream.open("example.com").join();
        var features = List.<StreamFeature>of(
                new StreamFeature.TlsFeature(true),
                new StreamFeature.SaslFeature(List.of("PLAIN"), true));
        stream.negotiateFeatures(features);
        assertThat(stream.getFeatures()).hasSize(2);
    }

    @Test
    void testStateListener() {
        var states = new ArrayList<XmppStreamState>();
        stream.addStateListener(states::add);
        stream.open("example.com").join();
        assertThat(states).contains(XmppStreamState.CONNECTING, XmppStreamState.NEGOTIATING);
    }

    @Test
    void testDrainOutbound() {
        stream.open("example.com").join();
        var outbound = stream.drainOutbound();
        assertThat(outbound).isNotEmpty();
        // After drain, should be empty
        assertThat(stream.drainOutbound()).isEmpty();
    }

    @Test
    void testStreamId() {
        stream.setStreamId("stream-123");
        assertThat(stream.getStreamId()).isEqualTo("stream-123");
    }
}
