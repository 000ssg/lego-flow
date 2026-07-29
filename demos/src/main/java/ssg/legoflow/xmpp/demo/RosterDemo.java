package ssg.legoflow.xmpp.demo;

import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.client.XmppClientConfig;
import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.roster.Roster;
import ssg.legoflow.xmpp.roster.RosterItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Roster management demo: add/remove contacts, groups.
 *
 * @since 1.0.0
 */
public class RosterDemo {

    private static final Logger LOG = LoggerFactory.getLogger(RosterDemo.class);

    private final XmppClient client;

    /**
     * Creates the roster demo.
     */
    public RosterDemo() {
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
    }

    /**
     * Demonstrates roster operations.
     */
    public void demonstrateRosterOperations() {
        var roster = client.getRoster();

        // Add contacts
        var alice = JID.parse("alice@example.com");
        var bob = JID.parse("bob@example.com");
        var carol = JID.parse("carol@example.com");

        roster.addItem(alice, "Alice", "Friends", "Work");
        roster.addItem(bob, "Bob", "Work");
        roster.addItem(carol, "Carol", "Friends");

        LOG.info("Added 3 contacts, roster size: {}", roster.size());
        LOG.info("Groups: {}", roster.getGroups());
        LOG.info("Friends: {}", roster.getItemsByGroup("Friends").size());
        LOG.info("Work: {}", roster.getItemsByGroup("Work").size());

        // Update contact
        var updatedBob = new RosterItem(bob, "Robert",
                RosterItem.SubscriptionType.BOTH, List.of("Work", "Friends"));
        roster.updateItem(updatedBob);
        LOG.info("Updated Bob to Robert");

        // Remove contact
        roster.removeItem(carol);
        LOG.info("Removed Carol, roster size: {}", roster.size());
    }

    /** @return the client */
    public XmppClient getClient() { return client; }

    /** @return the roster */
    public Roster getRoster() { return client.getRoster(); }

    /** Shuts down the client. */
    public void shutdown() { client.close(); }
}
