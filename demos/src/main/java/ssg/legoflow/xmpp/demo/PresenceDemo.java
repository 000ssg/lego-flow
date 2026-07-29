package ssg.legoflow.xmpp.demo;

import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.client.XmppClientConfig;
import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.PresenceStanza;
import ssg.legoflow.xmpp.core.PresenceStanza.PresenceShow;
import ssg.legoflow.xmpp.core.PresenceStanza.PresenceType;
import ssg.legoflow.xmpp.presence.PresenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Presence status demo: online/away/dnd, subscription management.
 *
 * @since 1.0.0
 */
public class PresenceDemo {

    private static final Logger LOG = LoggerFactory.getLogger(PresenceDemo.class);

    private final XmppClient client;
    private final List<PresenceStanza> receivedPresences = new ArrayList<>();

    /**
     * Creates the presence demo.
     */
    public PresenceDemo() {
        this.client = new XmppClient();
    }

    /**
     * Sets up the client.
     *
     * @param domain the XMPP domain
     */
    public void setup(String domain) {
        var config = XmppClientConfig.defaults("localhost", domain);
        client.connect(config).join();
        client.login("user", "password").join();
        client.getPresenceManager().addPresenceListener((jid, presence) -> receivedPresences.add(presence));
    }

    /**
     * Demonstrates setting various presence states.
     */
    public void demonstratePresenceStates() {
        var pm = client.getPresenceManager();
        pm.sendAvailable();
        LOG.info("Set status: Available");

        pm.sendPresence(PresenceShow.AWAY, "Grabbing coffee");
        LOG.info("Set status: Away - Grabbing coffee");

        pm.sendPresence(PresenceShow.DND, "In a meeting");
        LOG.info("Set status: DND - In a meeting");

        pm.sendPresence(PresenceShow.CHAT, "Let's talk!");
        LOG.info("Set status: Chat - Let's talk!");

        pm.sendUnavailable();
        LOG.info("Set status: Unavailable");
    }

    /**
     * Demonstrates subscription management.
     *
     * @param contactJid the contact to subscribe to
     */
    public void demonstrateSubscription(JID contactJid) {
        var pm = client.getPresenceManager();
        pm.subscribe(contactJid);
        LOG.info("Sent subscription request to {}", contactJid.toBareJid());

        // Simulate receiving subscription approval
        var approved = new PresenceStanza(UUID.randomUUID().toString(),
                contactJid, client.getLocalJid(), PresenceType.SUBSCRIBED,
                null, null, 0, List.of());
        pm.handlePresence(approved);
    }

    /**
     * Simulates receiving a presence update from a contact.
     *
     * @param fromJid the contact JID
     * @param show    the presence show
     * @param status  the status text
     */
    public void receivePresence(JID fromJid, PresenceShow show, String status) {
        var presence = new PresenceStanza(UUID.randomUUID().toString(),
                fromJid, client.getLocalJid(), PresenceType.AVAILABLE,
                show, status, 0, List.of());
        client.getPresenceManager().handlePresence(presence);
    }

    /** @return received presences */
    public List<PresenceStanza> getReceivedPresences() { return List.copyOf(receivedPresences); }

    /** @return the client */
    public XmppClient getClient() { return client; }

    /** Shuts down the client. */
    public void shutdown() { client.close(); }
}
