package ssg.legoflow.xmpp.demo;

import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.client.XmppClientConfig;
import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.MessageStanza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplest XMPP demo: two clients exchange messages.
 *
 * @since 1.0.0
 */
public class SimpleChatDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleChatDemo.class);

    private final XmppClient alice;
    private final XmppClient bob;
    private final List<MessageStanza> aliceMessages = new ArrayList<>();
    private final List<MessageStanza> bobMessages = new ArrayList<>();

    /**
     * Creates the chat demo with two clients.
     */
    public SimpleChatDemo() {
        this.alice = new XmppClient();
        this.bob = new XmppClient();
    }

    /**
     * Sets up both clients: connect and login.
     *
     * @param domain the XMPP domain
     */
    public void setup(String domain) {
        var config = XmppClientConfig.defaults("localhost", domain);
        alice.connect(config).join();
        bob.connect(config).join();
        alice.login("alice", "password").join();
        bob.login("bob", "password").join();

        alice.addMessageListener(aliceMessages::add);
        bob.addMessageListener(bobMessages::add);
    }

    /**
     * Sends a message from Alice to Bob.
     *
     * @param body the message body
     */
    public void aliceSays(String body) {
        alice.sendMessage(bob.getLocalJid(), body);
        // Simulate delivery
        var msg = MessageStanza.chat("msg-" + System.nanoTime(),
                alice.getLocalJid(), bob.getLocalJid(), body);
        bob.handleStanza(msg);
        LOG.info("Alice -> Bob: {}", body);
    }

    /**
     * Sends a message from Bob to Alice.
     *
     * @param body the message body
     */
    public void bobSays(String body) {
        bob.sendMessage(alice.getLocalJid(), body);
        var msg = MessageStanza.chat("msg-" + System.nanoTime(),
                bob.getLocalJid(), alice.getLocalJid(), body);
        alice.handleStanza(msg);
        LOG.info("Bob -> Alice: {}", body);
    }

    /**
     * Returns messages received by Alice.
     *
     * @return the list of messages
     */
    public List<MessageStanza> getAliceMessages() {
        return List.copyOf(aliceMessages);
    }

    /**
     * Returns messages received by Bob.
     *
     * @return the list of messages
     */
    public List<MessageStanza> getBobMessages() {
        return List.copyOf(bobMessages);
    }

    /**
     * Returns Alice's client.
     *
     * @return the client
     */
    public XmppClient getAlice() {
        return alice;
    }

    /**
     * Returns Bob's client.
     *
     * @return the client
     */
    public XmppClient getBob() {
        return bob;
    }

    /**
     * Shuts down both clients.
     */
    public void shutdown() {
        alice.close();
        bob.close();
    }
}
