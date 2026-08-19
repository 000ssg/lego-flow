package ssg.legoflow.wamp.core.router;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.transport.WampTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
/**
 * WAMP Broker — manages pub/sub topic subscriptions and delivers events to subscribers.
 * Supports Advanced Profile features: pattern-based subscriptions (prefix, wildcard),
 * publisher exclusion, subscriber black/white listing, publisher identification, and
 * event retention.
 *
 * @since 0.1.0
 */
public class Broker {

    private final AtomicLong subscriptionIdCounter = new AtomicLong(1);
    private final AtomicLong publicationIdCounter = new AtomicLong(1);

    /** topic -> set of (subscriptionId, transport, sessionId) entries */
    private final Map<String, Set<SubscriptionEntry>> topicSubscriptions = new ConcurrentHashMap<>();

    /** Prefix-match subscriptions: prefix -> set of entries */
    private final Map<String, Set<SubscriptionEntry>> prefixSubscriptions = new ConcurrentHashMap<>();

    /** Wildcard-match subscriptions: pattern -> set of entries */
    private final Map<String, Set<SubscriptionEntry>> wildcardSubscriptions = new ConcurrentHashMap<>();

    /** Retained events: topic -> last published event */
    private final Map<String, WampMessage.Event> retainedEvents = new ConcurrentHashMap<>();

    /**
     * Handles a Subscribe request from a client.
     *
     * @param subscribe the subscribe message
     * @param transport the client's transport (to send events back)
     * @return the Subscribed confirmation message
     */
    public WampMessage.Subscribed handleSubscribe(WampMessage.Subscribe subscribe, WampTransport transport) {
        return handleSubscribe(subscribe, transport, 0);
    }

    /**
     * Handles a Subscribe request from a client with session tracking.
     *
     * @param subscribe the subscribe message
     * @param transport the client's transport
     * @param sessionId the client's session ID
     * @return the Subscribed confirmation message
     */
    public WampMessage.Subscribed handleSubscribe(WampMessage.Subscribe subscribe, WampTransport transport, long sessionId) {
        String topic = subscribe.topic();
        long subscriptionId = subscriptionIdCounter.getAndIncrement();
        var entry = new SubscriptionEntry(subscriptionId, transport, sessionId, topic);

        String matchPolicy = getMatchPolicy(subscribe.options());

        switch (matchPolicy) {
            case "prefix" -> prefixSubscriptions
                    .computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>())
                    .add(entry);
            case "wildcard" -> wildcardSubscriptions
                    .computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>())
                    .add(entry);
            default -> topicSubscriptions
                    .computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>())
                    .add(entry);
        }

        // Deliver retained event if available (exact match only)
        if ("exact".equals(matchPolicy)) {
            var retained = retainedEvents.get(topic);
            if (retained != null) {
                transport.send(new WampMessage.Event(
                        subscriptionId, retained.publicationId(),
                        retained.details(), retained.args()));
            }
        }

        return new WampMessage.Subscribed(subscribe.requestId(), subscriptionId);
    }

    /**
     * Handles an Unsubscribe request from a client.
     *
     * @param unsubscribe the unsubscribe message
     * @return the Unsubscribed confirmation message
     */
    public WampMessage.Unsubscribed handleUnsubscribe(WampMessage.Unsubscribe unsubscribe) {
        removeSubscription(unsubscribe.subscriptionId(), topicSubscriptions);
        removeSubscription(unsubscribe.subscriptionId(), prefixSubscriptions);
        removeSubscription(unsubscribe.subscriptionId(), wildcardSubscriptions);
        return new WampMessage.Unsubscribed(unsubscribe.requestId());
    }

    /**
     * Handles a Publish request by delivering the event to all matching subscribers.
     * Supports publisher exclusion, subscriber black/white listing, publisher identification,
     * and event retention.
     *
     * @param publish         the publish message
     * @param publisher       the publisher's transport
     * @param publisherSessionId the publisher's session ID (for identification and exclusion)
     * @return the Published confirmation message
     */
    public WampMessage.Published handlePublish(WampMessage.Publish publish, WampTransport publisher, long publisherSessionId) {
        long publicationId = publicationIdCounter.getAndIncrement();
        var options = publish.options();

        // Publisher exclusion (default: exclude publisher)
        boolean excludeMe = true;
        if (options.containsKey("exclude_me")) {
            excludeMe = Boolean.TRUE.equals(options.get("exclude_me"));
        }

        // Subscriber black/white listing
        @SuppressWarnings("unchecked")
        var eligible = options.containsKey("eligible")
                ? (List<Number>) options.get("eligible") : null;
        @SuppressWarnings("unchecked")
        var exclude = options.containsKey("exclude")
                ? (List<Number>) options.get("exclude") : null;

        // Publisher identification
        boolean discloseMe = Boolean.TRUE.equals(options.get("disclose_me"));

        var eventDetails = new java.util.HashMap<String, Object>();
        if (discloseMe) {
            eventDetails.put("publisher", publisherSessionId);
        }

        // Collect all matching subscribers
        var matchingEntries = new ArrayList<SubscriptionEntry>();

        // Exact matches
        var exactSubs = topicSubscriptions.get(publish.topic());
        if (exactSubs != null) {
            matchingEntries.addAll(exactSubs);
        }

        // Prefix matches
        for (var entry : prefixSubscriptions.entrySet()) {
            if (publish.topic().startsWith(entry.getKey())) {
                matchingEntries.addAll(entry.getValue());
            }
        }

        // Wildcard matches
        for (var entry : wildcardSubscriptions.entrySet()) {
            if (matchesWildcard(publish.topic(), entry.getKey())) {
                matchingEntries.addAll(entry.getValue());
            }
        }

        // Deliver event to each matching subscriber with filtering
        for (var entry : matchingEntries) {
            // Publisher exclusion
            if (excludeMe && entry.sessionId() == publisherSessionId && publisherSessionId != 0) {
                continue;
            }

            // Subscriber blacklist
            if (exclude != null && containsSessionId(exclude, entry.sessionId())) {
                continue;
            }

            // Subscriber whitelist
            if (eligible != null && !containsSessionId(eligible, entry.sessionId())) {
                continue;
            }

            entry.transport().send(new WampMessage.Event(
                    entry.subscriptionId(), publicationId,
                    Map.copyOf(eventDetails), publish.args()));
        }

        // Event retention
        if (Boolean.TRUE.equals(options.get("retain"))) {
            retainedEvents.put(publish.topic(), new WampMessage.Event(
                    0, publicationId, Map.copyOf(eventDetails), publish.args()));
        }

        return new WampMessage.Published(publish.requestId(), publicationId);
    }

    /**
     * Handles a Publish request (backwards-compatible overload without session tracking).
     *
     * @param publish   the publish message
     * @param publisher the publisher's transport
     * @return the Published confirmation message
     */
    public WampMessage.Published handlePublish(WampMessage.Publish publish, WampTransport publisher) {
        return handlePublish(publish, publisher, 0);
    }

    /**
     * Returns the number of subscriptions for a given topic (exact match only).
     *
     * @param topic the topic URI
     * @return subscription count
     */
    public int getSubscriptionCount(String topic) {
        var entries = topicSubscriptions.get(topic);
        return entries != null ? entries.size() : 0;
    }


    /**
     * Returns the set of all topics that have active exact-match subscriptions.
     *
     * @return unmodifiable set of topic URIs
     * @since 0.2.0
     */
    public Set<String> getSubscriptionTopics() {
        return Set.copyOf(topicSubscriptions.keySet());
    }

    /**
     * Returns the set of all prefix-matched subscription patterns.
     *
     * @return unmodifiable set of prefix patterns
     * @since 0.2.0
     */
    public Set<String> getPrefixSubscriptionPatterns() {
        return Set.copyOf(prefixSubscriptions.keySet());
    }

    /**
     * Returns the set of all wildcard-matched subscription patterns.
     *
     * @return unmodifiable set of wildcard patterns
     * @since 0.2.0
     */
    public Set<String> getWildcardSubscriptionPatterns() {
        return Set.copyOf(wildcardSubscriptions.keySet());
    }

    /**
     * Returns the retained event for a topic, if any.
     *
     * @param topic the topic URI
     * @return the retained event, or {@code null}
     */
    public WampMessage.Event getRetainedEvent(String topic) {
        return retainedEvents.get(topic);
    }

    /**
     * Clears all retained events.
     */
    public void clearRetainedEvents() {
        retainedEvents.clear();
    }

    /**
     * Checks whether a topic URI matches a wildcard pattern.
     * Wildcard patterns use empty segments to match any single component.
     * E.g., "com..bar" matches "com.anything.bar".
     *
     * @param topic   the topic URI to check
     * @param pattern the wildcard pattern
     * @return {@code true} if the topic matches
     */
    public static boolean matchesWildcard(String topic, String pattern) {
        String[] topicParts = topic.split("\\.", -1);
        String[] patternParts = pattern.split("\\.", -1);
        if (topicParts.length != patternParts.length) return false;
        for (int i = 0; i < patternParts.length; i++) {
            if (!patternParts[i].isEmpty() && !patternParts[i].equals(topicParts[i])) {
                return false;
            }
        }
        return true;
    }

    private String getMatchPolicy(Map<String, Object> options) {
        if (options == null) return "exact";
        var match = options.get("match");
        if (match instanceof String s) return s;
        return "exact";
    }

    private void removeSubscription(long subscriptionId, Map<String, Set<SubscriptionEntry>> subscriptions) {
        subscriptions.entrySet().removeIf(entry ->
            entry.getValue().removeIf(e -> e.subscriptionId() == subscriptionId) && entry.getValue().isEmpty()
        );
    }

    private boolean containsSessionId(List<Number> sessionIds, long sessionId) {
        for (var id : sessionIds) {
            if (id.longValue() == sessionId) return true;
        }
        return false;
    }

    /**
     * Internal record associating a subscription ID with a transport endpoint, session ID, and topic.
     * The topic field stores the original subscription topic for reflection/introspection.
     *
     * @since 0.1.0 (topic field added in 0.2.0)
     */
    record SubscriptionEntry(long subscriptionId, WampTransport transport, long sessionId, String topic) {
        SubscriptionEntry(long subscriptionId, WampTransport transport, long sessionId) {
            this(subscriptionId, transport, sessionId, null);
        }
        SubscriptionEntry(long subscriptionId, WampTransport transport) {
            this(subscriptionId, transport, 0, null);
        }
    }
}
