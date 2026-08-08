package ssg.legoflow.xmpp.roster;

import ssg.legoflow.xmpp.core.JID;

import java.util.List;

/**
 * Listener for roster change events.
 *
 * @since 0.1.0
 */
public interface RosterListener {

    /**
     * Called when the initial roster has been loaded.
     *
     * @param items the roster items
     */
    void onRosterLoaded(List<RosterItem> items);

    /**
     * Called when a new item is added to the roster.
     *
     * @param item the added item
     */
    void onItemAdded(RosterItem item);

    /**
     * Called when an existing roster item is updated.
     *
     * @param item the updated item
     */
    void onItemUpdated(RosterItem item);

    /**
     * Called when an item is removed from the roster.
     *
     * @param jid the JID of the removed item
     */
    void onItemRemoved(JID jid);
}
