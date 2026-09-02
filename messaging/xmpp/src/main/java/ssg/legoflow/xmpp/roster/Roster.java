package ssg.legoflow.xmpp.roster;

import ssg.legoflow.xmpp.core.JID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
/**
 * XMPP roster management (RFC 6121).
 *
 * <p>Maintains the contact list and handles roster push notifications
 * from the server for real-time updates.
 *
 * @since 0.1.0
 */
public class Roster {

    private static final Logger LOG = LoggerFactory.getLogger(Roster.class);

    private final Map<String, RosterItem> items = new ConcurrentHashMap<>();
    private final List<RosterListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean loaded;

    /**
     * Creates a new empty roster.
     */
    public Roster() {
        this.loaded = false;
    }

    /**
     * Returns all roster items.
     *
     * @return an unmodifiable list of roster items
     */
    public List<RosterItem> getItems() {
        return List.copyOf(items.values());
    }

    /**
     * Returns a roster item by JID.
     *
     * @param jid the JID to look up
     * @return the roster item, or null if not found
     */
    public RosterItem getItem(JID jid) {
        return items.get(jid.toBareJid());
    }

    /**
     * Adds a new item to the roster.
     *
     * @param jid    the contact JID
     * @param name   the display name
     * @param groups the groups to assign
     */
    public void addItem(JID jid, String name, String... groups) {
        Objects.requireNonNull(jid, "jid must not be null");
        var item = new RosterItem(jid.toBare(), name,
                RosterItem.SubscriptionType.NONE, List.of(groups));
        items.put(jid.toBareJid(), item);
        LOG.info("Added roster item: {}", jid.toBareJid());
        for (var listener : listeners) {
            listener.onItemAdded(item);
        }
    }

    /**
     * Removes an item from the roster.
     *
     * @param jid the JID to remove
     */
    public void removeItem(JID jid) {
        Objects.requireNonNull(jid, "jid must not be null");
        var removed = items.remove(jid.toBareJid());
        if (removed != null) {
            LOG.info("Removed roster item: {}", jid.toBareJid());
            for (var listener : listeners) {
                listener.onItemRemoved(jid);
            }
        }
    }

    /**
     * Updates an existing roster item.
     *
     * @param item the updated item
     */
    public void updateItem(RosterItem item) {
        Objects.requireNonNull(item, "item must not be null");
        items.put(item.jid().toBareJid(), item);
        LOG.info("Updated roster item: {}", item.jid().toBareJid());
        for (var listener : listeners) {
            listener.onItemUpdated(item);
        }
    }

    /**
     * Handles a roster push from the server.
     *
     * @param item the pushed roster item
     */
    public void handleRosterPush(RosterItem item) {
        Objects.requireNonNull(item, "item must not be null");
        if (item.subscription() == RosterItem.SubscriptionType.REMOVE) {
            removeItem(item.jid());
        } else if (items.containsKey(item.jid().toBareJid())) {
            updateItem(item);
        } else {
            items.put(item.jid().toBareJid(), item);
            for (var listener : listeners) {
                listener.onItemAdded(item);
            }
        }
    }

    /**
     * Loads the initial roster.
     *
     * @param rosterItems the items to load
     */
    public void load(List<RosterItem> rosterItems) {
        items.clear();
        for (var item : rosterItems) {
            items.put(item.jid().toBareJid(), item);
        }
        this.loaded = true;
        LOG.info("Roster loaded with {} items", rosterItems.size());
        for (var listener : listeners) {
            listener.onRosterLoaded(getItems());
        }
    }

    /**
     * Returns items belonging to a specific group.
     *
     * @param group the group name
     * @return the items in the group
     */
    public List<RosterItem> getItemsByGroup(String group) {
        return items.values().stream()
                .filter(item -> item.groups().contains(group))
                .collect(Collectors.toList());
    }

    /**
     * Returns all group names in the roster.
     *
     * @return the set of group names
     */
    public List<String> getGroups() {
        return items.values().stream()
                .flatMap(item -> item.groups().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns whether the roster has been loaded.
     *
     * @return true if loaded
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns the number of items in the roster.
     *
     * @return the item count
     */
    public int size() {
        return items.size();
    }

    /**
     * Adds a roster listener.
     *
     * @param listener the listener to add
     */
    public void addListener(RosterListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a roster listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(RosterListener listener) {
        listeners.remove(listener);
    }
}
