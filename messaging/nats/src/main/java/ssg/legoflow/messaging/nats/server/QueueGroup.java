package ssg.legoflow.messaging.nats.server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Queue group for round-robin message distribution among subscribers.
 *
 * <p>When multiple subscribers share the same queue group name,
 * messages are distributed round-robin among the group members
 * rather than being sent to all subscribers.
 *
 * @since 0.1.0
 */
public final class QueueGroup {

    private final String name;
    private final List<SubscriptionEntry> members = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    /**
     * Creates a new queue group.
     *
     * @param name the group name
     */
    public QueueGroup(String name) {
        this.name = name;
    }

    /**
     * Returns the group name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Adds a member to this group.
     *
     * @param entry the subscription entry
     */
    public void addMember(SubscriptionEntry entry) {
        members.add(entry);
    }

    /**
     * Removes a member from this group.
     *
     * @param entry the subscription entry to remove
     * @return true if removed
     */
    public boolean removeMember(SubscriptionEntry entry) {
        return members.remove(entry);
    }

    /**
     * Selects the next member using round-robin.
     *
     * @return the selected member, or null if empty
     */
    public SubscriptionEntry nextMember() {
        if (members.isEmpty()) return null;
        int idx = Math.abs(roundRobinIndex.getAndIncrement() % members.size());
        return members.get(idx);
    }

    /**
     * Returns the number of members.
     *
     * @return the member count
     */
    public int size() {
        return members.size();
    }

    /**
     * Returns whether this group is empty.
     *
     * @return true if no members
     */
    public boolean isEmpty() {
        return members.isEmpty();
    }

    /**
     * Returns all members.
     *
     * @return list of members
     */
    public List<SubscriptionEntry> members() {
        return List.copyOf(members);
    }
}
