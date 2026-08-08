package ssg.legoflow.xmpp.presence;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.PresenceStanza;

/**
 * Functional interface for receiving presence change notifications.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface PresenceListener {

    /**
     * Called when the presence of a contact changes.
     *
     * @param jid      the JID whose presence changed
     * @param presence the new presence stanza
     */
    void onPresenceChanged(JID jid, PresenceStanza presence);
}
