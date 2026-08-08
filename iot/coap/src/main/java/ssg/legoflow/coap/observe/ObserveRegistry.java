package ssg.legoflow.coap.observe;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry for managing CoAP observe relationships (RFC 7641).
 *
 * <p>Tracks registrations from observers to resources and supports
 * notification delivery to all observers of a given resource path.
 *
 * @since 0.1.0
 */
public final class ObserveRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ObserveRegistry.class);

    /** Key for looking up relations by token. */
    private final Map<TokenKey, ObserveRelation> byToken = new ConcurrentHashMap<>();

    /** Observers grouped by resource path. */
    private final Map<String, CopyOnWriteArrayList<ObserveRelation>> byPath = new ConcurrentHashMap<>();

    /**
     * Registers a new observe relation for the given token, resource path, and observer.
     *
     * @param token    the observation token
     * @param path     the resource path being observed
     * @param observer the observer's socket address
     * @return the created {@link ObserveRelation}
     * @throws NullPointerException if any argument is {@code null}
     * @since 0.1.0
     */
    public ObserveRelation register(byte[] token, String path, SocketAddress observer) {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(observer, "observer must not be null");

        var relation = new ObserveRelation(token, path, observer);
        var key = new TokenKey(token);

        // Remove any existing relation with the same token
        var existing = byToken.put(key, relation);
        if (existing != null) {
            existing.cancel();
            removeFromPathMap(existing);
        }

        byPath.computeIfAbsent(path, k -> new CopyOnWriteArrayList<>()).add(relation);
        LOG.debug("Registered observe relation: {}", relation);
        return relation;
    }

    /**
     * Deregisters the observe relation with the given token.
     *
     * @param token the observation token to deregister
     * @since 0.1.0
     */
    public void deregister(byte[] token) {
        Objects.requireNonNull(token, "token must not be null");
        var key = new TokenKey(token);
        var relation = byToken.remove(key);
        if (relation != null) {
            relation.cancel();
            removeFromPathMap(relation);
            LOG.debug("Deregistered observe relation: {}", relation);
        }
    }

    /**
     * Returns all active observers for the given resource path.
     *
     * @param path the resource path
     * @return an unmodifiable list of active observe relations
     * @since 0.1.0
     */
    public List<ObserveRelation> getObservers(String path) {
        Objects.requireNonNull(path, "path must not be null");
        var relations = byPath.get(path);
        if (relations == null) {
            return Collections.emptyList();
        }
        var active = new ArrayList<ObserveRelation>();
        for (var relation : relations) {
            if (relation.isActive()) {
                active.add(relation);
            }
        }
        return Collections.unmodifiableList(active);
    }

    /**
     * Creates and returns notification messages for all observers of a given path.
     *
     * <p>Each notification carries the observer's token, an incremented sequence
     * number in the Observe option, and the provided notification payload.
     *
     * @param path         the resource path
     * @param notification a template notification message (code, payload, options)
     * @return a list of (relation, message) pairs for each active observer
     * @since 0.1.0
     */
    public List<NotificationEntry> notifyObservers(String path, CoapMessage notification) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(notification, "notification must not be null");

        var observers = getObservers(path);
        var entries = new ArrayList<NotificationEntry>();

        for (var relation : observers) {
            int seqNum = relation.nextSequenceNumber();
            var msg = CoapMessage.builder()
                    .type(CoapType.NON_CONFIRMABLE)
                    .code(notification.code())
                    .messageId(seqNum & 0xFFFF)
                    .token(relation.token())
                    .option(CoapOption.observe(seqNum))
                    .payload(notification.payload())
                    .build();

            // Copy content format from notification if present
            var cf = notification.getOption(CoapOption.CONTENT_FORMAT);
            if (cf != null) {
                msg = CoapMessage.builder()
                        .type(CoapType.NON_CONFIRMABLE)
                        .code(notification.code())
                        .messageId(seqNum & 0xFFFF)
                        .token(relation.token())
                        .option(CoapOption.observe(seqNum))
                        .option(cf)
                        .payload(notification.payload())
                        .build();
            }

            entries.add(new NotificationEntry(relation, msg));
        }

        return entries;
    }

    /**
     * Returns the total number of registered (active or inactive) relations.
     *
     * @return the total registration count
     * @since 0.1.0
     */
    public int size() {
        return byToken.size();
    }

    /**
     * Removes all registrations.
     *
     * @since 0.1.0
     */
    public void clear() {
        for (var relation : byToken.values()) {
            relation.cancel();
        }
        byToken.clear();
        byPath.clear();
    }

    private void removeFromPathMap(ObserveRelation relation) {
        var list = byPath.get(relation.resourcePath());
        if (list != null) {
            list.remove(relation);
            if (list.isEmpty()) {
                byPath.remove(relation.resourcePath());
            }
        }
    }

    /**
     * An entry pairing an observe relation with its notification message.
     *
     * @param relation     the observe relation
     * @param notification the notification message to send
     * @since 0.1.0
     */
    public record NotificationEntry(ObserveRelation relation, CoapMessage notification) {
    }

    /**
     * Wrapper for byte[] to use as a map key with proper equals/hashCode.
     */
    private record TokenKey(byte[] token) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TokenKey that)) return false;
            return Arrays.equals(token, that.token);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(token);
        }
    }
}
