package ssg.legoflow.messaging.nats.subject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry mapping subscriptions to subjects with wildcard support.
 *
 * <p>Maintains a map of subscription patterns to registered entries. When
 * matching a published subject, all patterns (including wildcards) are
 * evaluated.
 *
 * @param <T> the subscription entry type
 * @since 1.0.0
 */
public final class SubscriptionRegistry<T> {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<T>> exactSubs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<T>> wildcardSubs = new ConcurrentHashMap<>();

    /**
     * Registers a subscription entry for the given subject pattern.
     *
     * @param pattern the subject pattern (may contain wildcards)
     * @param entry   the subscription entry
     */
    public void subscribe(String pattern, T entry) {
        Subject subject = Subject.of(pattern);
        if (subject.hasWildcards()) {
            wildcardSubs.computeIfAbsent(pattern, k -> new CopyOnWriteArrayList<>()).add(entry);
        } else {
            exactSubs.computeIfAbsent(pattern, k -> new CopyOnWriteArrayList<>()).add(entry);
        }
    }

    /**
     * Removes a subscription entry for the given subject pattern.
     *
     * @param pattern the subject pattern
     * @param entry   the subscription entry to remove
     * @return true if the entry was found and removed
     */
    public boolean unsubscribe(String pattern, T entry) {
        Subject subject = Subject.of(pattern);
        var map = subject.hasWildcards() ? wildcardSubs : exactSubs;
        var list = map.get(pattern);
        if (list != null) {
            boolean removed = list.remove(entry);
            if (list.isEmpty()) {
                map.remove(pattern);
            }
            return removed;
        }
        return false;
    }

    /**
     * Finds all subscription entries matching the given published subject.
     *
     * @param subject the published subject (no wildcards)
     * @return list of matching entries
     */
    public List<T> match(String subject) {
        var result = new ArrayList<T>();

        // Check exact matches
        var exact = exactSubs.get(subject);
        if (exact != null) {
            result.addAll(exact);
        }

        // Check wildcard patterns
        for (var entry : wildcardSubs.entrySet()) {
            if (SubjectMatcher.matches(entry.getKey(), subject)) {
                result.addAll(entry.getValue());
            }
        }

        return result;
    }

    /**
     * Returns the total number of subscription entries.
     *
     * @return the count
     */
    public int size() {
        int count = 0;
        for (var list : exactSubs.values()) count += list.size();
        for (var list : wildcardSubs.values()) count += list.size();
        return count;
    }

    /**
     * Removes all subscriptions.
     */
    public void clear() {
        exactSubs.clear();
        wildcardSubs.clear();
    }

    /**
     * Returns all registered subscription patterns.
     *
     * @return set of patterns
     */
    public Set<String> patterns() {
        var result = new HashSet<String>();
        result.addAll(exactSubs.keySet());
        result.addAll(wildcardSubs.keySet());
        return Collections.unmodifiableSet(result);
    }
}
