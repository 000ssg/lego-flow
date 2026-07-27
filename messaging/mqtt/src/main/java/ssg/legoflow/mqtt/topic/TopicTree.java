package ssg.legoflow.mqtt.topic;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trie-based topic matching data structure for efficient subscriber lookup.
 *
 * <p>Supports MQTT wildcard topic filters ({@code +} and {@code #}) and provides
 * thread-safe subscribe, unsubscribe, and matching operations using
 * {@link ConcurrentHashMap}-based trie nodes.
 *
 * @param <T> the subscriber type
 * @since 1.0.0
 */
public final class TopicTree<T> {

    private final TrieNode<T> root = new TrieNode<>();

    /**
     * Subscribes a subscriber to the given topic filter.
     *
     * @param filter     the topic filter (may contain wildcards)
     * @param subscriber the subscriber to add
     * @throws NullPointerException if filter or subscriber is null
     */
    public void subscribe(String filter, T subscriber) {
        Objects.requireNonNull(filter, "Topic filter must not be null");
        Objects.requireNonNull(subscriber, "Subscriber must not be null");

        String[] levels = filter.split("/", -1);
        TrieNode<T> current = root;
        for (String level : levels) {
            current = current.children.computeIfAbsent(level, k -> new TrieNode<>());
        }
        current.subscribers.add(subscriber);
    }

    /**
     * Unsubscribes a subscriber from the given topic filter.
     *
     * @param filter     the topic filter
     * @param subscriber the subscriber to remove
     * @throws NullPointerException if filter or subscriber is null
     */
    public void unsubscribe(String filter, T subscriber) {
        Objects.requireNonNull(filter, "Topic filter must not be null");
        Objects.requireNonNull(subscriber, "Subscriber must not be null");

        String[] levels = filter.split("/", -1);
        TrieNode<T> current = root;
        for (String level : levels) {
            current = current.children.get(level);
            if (current == null) {
                return;
            }
        }
        current.subscribers.remove(subscriber);
    }

    /**
     * Returns all subscribers whose topic filters match the given topic name.
     *
     * @param topicName the concrete topic name (no wildcards)
     * @return the set of matching subscribers (never null)
     * @throws NullPointerException if topicName is null
     */
    public Set<T> getMatchingSubscribers(String topicName) {
        Objects.requireNonNull(topicName, "Topic name must not be null");
        String[] levels = topicName.split("/", -1);
        Set<T> result = ConcurrentHashMap.newKeySet();
        match(root, levels, 0, topicName, result);
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the total number of unique subscribers in the tree.
     *
     * @return the subscriber count
     */
    public int subscriberCount() {
        Set<T> all = new HashSet<>();
        collectAll(root, all);
        return all.size();
    }

    // --- Private helpers ---

    private void match(TrieNode<T> node, String[] levels, int index,
                       String topicName, Set<T> result) {
        if (node == null) {
            return;
        }

        if (index == levels.length) {
            result.addAll(node.subscribers);
            // Check for trailing # wildcard
            TrieNode<T> hashNode = node.children.get("#");
            if (hashNode != null) {
                result.addAll(hashNode.subscribers);
            }
            return;
        }

        String currentLevel = levels[index];

        // Topics starting with $ should not match + or # at root level
        boolean isSystemTopic = index == 0 && currentLevel.startsWith("$");

        // Exact match
        match(node.children.get(currentLevel), levels, index + 1, topicName, result);

        if (!isSystemTopic) {
            // Single-level wildcard
            match(node.children.get("+"), levels, index + 1, topicName, result);

            // Multi-level wildcard
            TrieNode<T> hashNode = node.children.get("#");
            if (hashNode != null) {
                result.addAll(hashNode.subscribers);
            }
        }
    }

    private void collectAll(TrieNode<T> node, Set<T> result) {
        result.addAll(node.subscribers);
        for (TrieNode<T> child : node.children.values()) {
            collectAll(child, result);
        }
    }

    /**
     * Thread-safe trie node using ConcurrentHashMap for children.
     */
    private static final class TrieNode<T> {
        final ConcurrentHashMap<String, TrieNode<T>> children = new ConcurrentHashMap<>();
        final Set<T> subscribers = ConcurrentHashMap.newKeySet();
    }
}
