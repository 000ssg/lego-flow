package ssg.legoflow.messaging.mqtt.topic;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registry for MQTT 5.0 shared subscriptions with round-robin delivery.
 *
 * <p>Shared subscriptions use the format {@code $share/group/topicFilter}.
 * When a message is published, only ONE subscriber from each share group
 * receives it, selected in round-robin fashion across the group's members.
 *
 * <p>This class is thread-safe.
 *
 * @since 0.2.0
 */
public final class SharedSubscriptionRegistry<T> {

    private final Map<String, List<T>> groups = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * Registers a subscriber in a share group.
     *
     * @param group   the share group name
     * @param filter  the effective topic filter (after stripping $share/group/)
     * @param subscriber the subscriber to register
     */
    public void register(String group, String filter, T subscriber) {
        groups.computeIfAbsent(group, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
              .add(subscriber);
        counters.putIfAbsent(group, new AtomicInteger(0));
    }

    /**
     * Removes a subscriber from a share group.
     *
     * @param group      the share group name
     * @param filter     the effective topic filter
     * @param subscriber the subscriber to remove
     */
    public void unregister(String group, String filter, T subscriber) {
        var members = groups.get(group);
        if (members != null) {
            members.remove(subscriber);
            if (members.isEmpty()) {
                groups.remove(group);
                counters.remove(group);
            }
        }
    }

    /**
     * Selects a subscriber from the given group using round-robin.
     *
     * @param group the share group name
     * @return the selected subscriber, or null if no members
     */
    public T selectNext(String group) {
        var members = groups.get(group);
        if (members == null || members.isEmpty()) return null;
        AtomicInteger counter = counters.get(group);
        if (counter == null) return null;
        int idx = counter.getAndIncrement() % members.size();
        if (idx < 0) idx += members.size();
        return members.get(idx);
    }

    /**
     * Returns all share group names.
     */
    public Collection<String> getGroups() {
        return groups.keySet();
    }

    /**
     * Returns the members of a share group.
     */
    public List<T> getMembers(String group) {
        return groups.get(group);
    }

    /**
     * Returns the total number of registered shared subscribers.
     */
    public int totalSubscribers() {
        return groups.values().stream().mapToInt(List::size).sum();
    }
}
