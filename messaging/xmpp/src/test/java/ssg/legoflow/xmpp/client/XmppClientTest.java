package ssg.legoflow.xmpp.client;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.MessageStanza;
import ssg.legoflow.xmpp.core.PresenceStanza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link XmppClient}.
 *
 * @since 1.0.0
 */
class XmppClientTest {

    private XmppClient client;

    @BeforeEach
    void setUp() {
        client = new XmppClient();
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void testInitialState() {
        assertThat(client.isConnected()).isFalse();
        assertThat(client.isAuthenticated()).isFalse();
    }

    @Test
    void testConnect() {
        var config = XmppClientConfig.defaults("localhost", "example.com");
        client.connect(config).join();
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testLogin() {
        var config = XmppClientConfig.defaults("localhost", "example.com");
        client.connect(config).join();
        boolean result = client.login("alice", "password").join();
        assertThat(result).isTrue();
        assertThat(client.isAuthenticated()).isTrue();
        assertThat(client.getLocalJid()).isNotNull();
        assertThat(client.getLocalJid().localpart()).isEqualTo("alice");
    }

    @Test
    void testDisconnect() {
        var config = XmppClientConfig.defaults("localhost", "example.com");
        client.connect(config).join();
        client.login("alice", "password").join();
        client.disconnect();
        assertThat(client.isConnected()).isFalse();
        assertThat(client.isAuthenticated()).isFalse();
    }

    @Test
    void testSendMessage() {
        var config = XmppClientConfig.defaults("localhost", "example.com");
        client.connect(config).join();
        client.login("alice", "password").join();
        assertThatNoException().isThrownBy(() ->
                client.sendMessage(JID.parse("bob@example.com"), "Hello!"));
    }

    @Test
    void testMessageListener() {
        var config = XmppClientConfig.defaults("localhost", "example.com");
        client.connect(config).join();
        client.login("alice", "password").join();

        var received = new ArrayList<MessageStanza>();
        client.addMessageListener(received::add);

        var msg = MessageStanza.chat("m1", JID.parse("bob@example.com"),
                client.getLocalJid(), "Hi Alice!");
        client.handleStanza(msg);
        assertThat(received).hasSize(1);
        assertThat(received.getFirst().body()).isEqualTo("Hi Alice!");
    }

    @Test
    void testPresenceManager() {
        var config = XmppClientConfig.defaults("localhost", "example.com");
        client.connect(config).join();
        client.login("alice", "password").join();
        assertThat(client.getPresenceManager()).isNotNull();
    }

    @Test
    void testSendPresence() {
        var config = XmppClientConfig.defaults("localhost", "example.com");
        client.connect(config).join();
        client.login("alice", "password").join();
        client.sendPresence(PresenceStanza.PresenceShow.AWAY, "BRB");
        assertThat(client.getPresenceManager().getCurrentPresence()).isNotNull();
    }

    @Test
    void testIoTManagers() {
        assertThat(client.getSensorManager()).isNotNull();
        assertThat(client.getControlManager()).isNotNull();
        assertThat(client.getDiscoveryManager()).isNotNull();
    }

    @Test
    void testRoster() {
        assertThat(client.getRoster()).isNotNull();
        assertThat(client.getRoster().size()).isEqualTo(0);
    }
}
