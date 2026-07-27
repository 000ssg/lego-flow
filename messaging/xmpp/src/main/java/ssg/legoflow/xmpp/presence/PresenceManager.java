package ssg.legoflow.xmpp.presence;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.PresenceStanza;
import ssg.legoflow.xmpp.core.PresenceStanza.PresenceShow;
import ssg.legoflow.xmpp.core.PresenceStanza.PresenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages presence status and subscriptions (RFC 6121).
 *
 * <p>Handles sending and receiving presence updates, subscription management,
 * and tracking the presence state of contacts.
 *
 * @since 1.0.0
 */
public class PresenceManager {

    private static final Logger LOG = LoggerFactory.getLogger(PresenceManager.class);

    private final Map<String, PresenceStanza> presenceMap = new ConcurrentHashMap<>();
    private final List<PresenceListener> listeners = new CopyOnWriteArrayList<>();
    private final List<PresenceStanza> sentPresences = new CopyOnWriteArrayList<>();

    private JID localJid;
    private PresenceStanza currentPresence;

    /**
     * Creates a new presence manager.
     *
     * @param localJid the local JID
     */
    public PresenceManager(JID localJid) {
        this.localJid = Objects.requireNonNull(localJid, "localJid must not be null");
    }

    /**
     * Sends a presence update with the given show and status.
     *
     * @param show   the presence show value
     * @param status the status text
     * @return the sent presence stanza
     */
    public PresenceStanza sendPresence(PresenceShow show, String status) {
        var presence = new PresenceStanza(
                UUID.randomUUID().toString(), localJid, null,
                PresenceType.AVAILABLE, show, status, 0, List.of());
        this.currentPresence = presence;
        sentPresences.add(presence);
        LOG.info("Sent presence: show={}, status={}", show, status);
        return presence;
    }

    /**
     * Sends an available presence.
     *
     * @return the sent presence stanza
     */
    public PresenceStanza sendAvailable() {
        return sendPresence(null, null);
    }

    /**
     * Sends an unavailable presence.
     *
     * @return the sent presence stanza
     */
    public PresenceStanza sendUnavailable() {
        var presence = new PresenceStanza(
                UUID.randomUUID().toString(), localJid, null,
                PresenceType.UNAVAILABLE, null, null, 0, List.of());
        this.currentPresence = presence;
        sentPresences.add(presence);
        LOG.info("Sent unavailable presence");
        return presence;
    }

    /**
     * Sends a subscription request to a contact.
     *
     * @param contact the contact JID
     * @return the sent presence stanza
     */
    public PresenceStanza subscribe(JID contact) {
        Objects.requireNonNull(contact, "contact must not be null");
        var presence = new PresenceStanza(
                UUID.randomUUID().toString(), localJid, contact,
                PresenceType.SUBSCRIBE, null, null, 0, List.of());
        sentPresences.add(presence);
        LOG.info("Sent subscription request to {}", contact.toBareJid());
        return presence;
    }

    /**
     * Unsubscribes from a contact's presence.
     *
     * @param contact the contact JID
     * @return the sent presence stanza
     */
    public PresenceStanza unsubscribe(JID contact) {
        Objects.requireNonNull(contact, "contact must not be null");
        var presence = new PresenceStanza(
                UUID.randomUUID().toString(), localJid, contact,
                PresenceType.UNSUBSCRIBE, null, null, 0, List.of());
        sentPresences.add(presence);
        LOG.info("Sent unsubscribe to {}", contact.toBareJid());
        return presence;
    }

    /**
     * Approves a subscription request from a contact.
     *
     * @param contact the contact JID
     * @return the sent presence stanza
     */
    public PresenceStanza approveSubscription(JID contact) {
        Objects.requireNonNull(contact, "contact must not be null");
        var presence = new PresenceStanza(
                UUID.randomUUID().toString(), localJid, contact,
                PresenceType.SUBSCRIBED, null, null, 0, List.of());
        sentPresences.add(presence);
        LOG.info("Approved subscription from {}", contact.toBareJid());
        return presence;
    }

    /**
     * Denies a subscription request from a contact.
     *
     * @param contact the contact JID
     * @return the sent presence stanza
     */
    public PresenceStanza denySubscription(JID contact) {
        Objects.requireNonNull(contact, "contact must not be null");
        var presence = new PresenceStanza(
                UUID.randomUUID().toString(), localJid, contact,
                PresenceType.UNSUBSCRIBED, null, null, 0, List.of());
        sentPresences.add(presence);
        LOG.info("Denied subscription from {}", contact.toBareJid());
        return presence;
    }

    /**
     * Returns the last known presence of a contact.
     *
     * @param jid the contact JID
     * @return the presence stanza, or null if unknown
     */
    public PresenceStanza getPresence(JID jid) {
        return presenceMap.get(jid.toBareJid());
    }

    /**
     * Handles an incoming presence stanza.
     *
     * @param presence the received presence
     */
    public void handlePresence(PresenceStanza presence) {
        if (presence.from() != null) {
            presenceMap.put(presence.from().toBareJid(), presence);
            LOG.debug("Updated presence for {}: type={}", presence.from().toBareJid(), presence.presenceType());
            for (var listener : listeners) {
                listener.onPresenceChanged(presence.from(), presence);
            }
        }
    }

    /**
     * Returns the current local presence.
     *
     * @return the current presence
     */
    public PresenceStanza getCurrentPresence() {
        return currentPresence;
    }

    /**
     * Returns all sent presence stanzas.
     *
     * @return the list of sent presences
     */
    public List<PresenceStanza> getSentPresences() {
        return List.copyOf(sentPresences);
    }

    /**
     * Adds a presence listener.
     *
     * @param listener the listener
     */
    public void addPresenceListener(PresenceListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a presence listener.
     *
     * @param listener the listener
     */
    public void removePresenceListener(PresenceListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns the local JID.
     *
     * @return the local JID
     */
    public JID getLocalJid() {
        return localJid;
    }
}
