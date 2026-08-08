package ssg.legoflow.xmpp.pubsub;

import ssg.legoflow.xmpp.core.JID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Manages Publish-Subscribe operations (XEP-0060).
 *
 * <p>Provides operations for creating/deleting nodes, publishing items,
 * subscribing/unsubscribing, and delivering notifications.
 *
 * @since 0.1.0
 */
public class PubSubManager {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubManager.class);

    /** The XEP-0060 PubSub namespace. */
    public static final String NAMESPACE = "http://jabber.org/protocol/pubsub";

    /** The XEP-0060 PubSub event namespace. */
    public static final String EVENT_NAMESPACE = "http://jabber.org/protocol/pubsub#event";

    /** The XEP-0060 PubSub owner namespace. */
    public static final String OWNER_NAMESPACE = "http://jabber.org/protocol/pubsub#owner";

    private final JID serviceJid;
    private final Map<String, PubSubNode> nodes = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, PubSubItem>> notificationListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new PubSub manager.
     *
     * @param serviceJid the PubSub service JID (e.g., pubsub.example.com)
     */
    public PubSubManager(JID serviceJid) {
        this.serviceJid = Objects.requireNonNull(serviceJid, "serviceJid must not be null");
    }

    /**
     * Creates a new PubSub node.
     *
     * @param nodeId the node identifier
     * @return the created node
     * @throws IllegalArgumentException if a node with this id already exists
     */
    public PubSubNode createNode(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodes.containsKey(nodeId)) {
            throw new IllegalArgumentException("Node already exists: " + nodeId);
        }
        var node = PubSubNode.leaf(nodeId);
        nodes.put(nodeId, node);
        LOG.info("Created PubSub node: {}", nodeId);
        return node;
    }

    /**
     * Creates a new PubSub node with the specified type.
     *
     * @param nodeId   the node identifier
     * @param nodeType the node type
     * @return the created node
     * @throws IllegalArgumentException if a node with this id already exists
     */
    public PubSubNode createNode(String nodeId, PubSubNode.NodeType nodeType) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(nodeType, "nodeType must not be null");
        if (nodes.containsKey(nodeId)) {
            throw new IllegalArgumentException("Node already exists: " + nodeId);
        }
        var node = new PubSubNode(nodeId, nodeType);
        nodes.put(nodeId, node);
        LOG.info("Created PubSub node: {} (type={})", nodeId, nodeType);
        return node;
    }

    /**
     * Deletes a PubSub node.
     *
     * @param nodeId the node identifier
     * @return true if the node was deleted
     */
    public boolean deleteNode(String nodeId) {
        var removed = nodes.remove(nodeId);
        if (removed != null) {
            LOG.info("Deleted PubSub node: {}", nodeId);
            return true;
        }
        return false;
    }

    /**
     * Returns a node by id.
     *
     * @param nodeId the node identifier
     * @return the node, or null if not found
     */
    public PubSubNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * Returns all nodes.
     *
     * @return the list of nodes
     */
    public List<PubSubNode> getNodes() {
        return List.copyOf(nodes.values());
    }

    /**
     * Publishes an item to a node.
     *
     * @param nodeId      the node identifier
     * @param payload     the item payload (XML content, may be null)
     * @param publisherJid the publisher's bare JID string
     * @return the published item
     * @throws IllegalArgumentException if the node does not exist
     */
    public PubSubItem publish(String nodeId, String payload, String publisherJid) {
        var node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }

        var item = new PubSubItem(
                UUID.randomUUID().toString(), payload, publisherJid, Instant.now());
        node.publishItem(item);

        LOG.debug("Published item {} to node {}", item.id(), nodeId);

        // Notify subscribers
        for (var sub : node.getSubscriptions()) {
            if (sub.state() == PubSubSubscription.State.SUBSCRIBED) {
                notifySubscriber(nodeId, item, sub);
            }
        }

        return item;
    }

    /**
     * Publishes an item with a specific id to a node.
     *
     * @param nodeId       the node identifier
     * @param itemId       the item identifier
     * @param payload      the item payload
     * @param publisherJid the publisher's bare JID string
     * @return the published item
     */
    public PubSubItem publish(String nodeId, String itemId, String payload, String publisherJid) {
        var node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }

        var item = new PubSubItem(itemId, payload, publisherJid, Instant.now());
        node.publishItem(item);

        LOG.debug("Published item {} to node {}", itemId, nodeId);

        for (var sub : node.getSubscriptions()) {
            if (sub.state() == PubSubSubscription.State.SUBSCRIBED) {
                notifySubscriber(nodeId, item, sub);
            }
        }

        return item;
    }

    /**
     * Retracts (deletes) an item from a node.
     *
     * @param nodeId the node identifier
     * @param itemId the item identifier
     * @return true if the item was retracted
     */
    public boolean retract(String nodeId, String itemId) {
        var node = nodes.get(nodeId);
        if (node == null) {
            return false;
        }
        return node.retractItem(itemId);
    }

    /**
     * Subscribes a JID to a node.
     *
     * @param nodeId        the node identifier
     * @param subscriberJid the subscriber's bare JID string
     * @return the subscription
     * @throws IllegalArgumentException if the node does not exist
     */
    public PubSubSubscription subscribe(String nodeId, String subscriberJid) {
        var node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }

        // Check for existing subscription
        var existing = node.getSubscription(subscriberJid);
        if (existing != null && existing.state() == PubSubSubscription.State.SUBSCRIBED) {
            return existing;
        }

        var sub = new PubSubSubscription(
                nodeId, subscriberJid, UUID.randomUUID().toString(),
                PubSubSubscription.State.SUBSCRIBED);
        node.addSubscription(sub);

        LOG.info("Subscribed {} to node {}", subscriberJid, nodeId);
        return sub;
    }

    /**
     * Unsubscribes a JID from a node.
     *
     * @param nodeId        the node identifier
     * @param subscriberJid the subscriber's bare JID string
     * @return true if the subscription was removed
     */
    public boolean unsubscribe(String nodeId, String subscriberJid) {
        var node = nodes.get(nodeId);
        if (node == null) {
            return false;
        }
        boolean removed = node.removeSubscription(subscriberJid);
        if (removed) {
            LOG.info("Unsubscribed {} from node {}", subscriberJid, nodeId);
        }
        return removed;
    }

    /**
     * Adds a notification listener that is called when an item is published.
     *
     * @param listener the listener accepting (nodeId, item)
     */
    public void addNotificationListener(BiConsumer<String, PubSubItem> listener) {
        notificationListeners.add(listener);
    }

    /**
     * Returns the service JID.
     *
     * @return the service JID
     */
    public JID getServiceJid() {
        return serviceJid;
    }

    /**
     * Generates the IQ XML for creating a node.
     *
     * @param nodeId the node identifier
     * @return the IQ XML
     */
    public String generateCreateNodeXml(String nodeId) {
        return "<iq type=\"set\" to=\"" + serviceJid.toBareJid() + "\">" +
                "<pubsub xmlns=\"" + NAMESPACE + "\">" +
                "<create node=\"" + nodeId + "\"/>" +
                "</pubsub></iq>";
    }

    /**
     * Generates the IQ XML for deleting a node.
     *
     * @param nodeId the node identifier
     * @return the IQ XML
     */
    public String generateDeleteNodeXml(String nodeId) {
        return "<iq type=\"set\" to=\"" + serviceJid.toBareJid() + "\">" +
                "<pubsub xmlns=\"" + OWNER_NAMESPACE + "\">" +
                "<delete node=\"" + nodeId + "\"/>" +
                "</pubsub></iq>";
    }

    /**
     * Generates the IQ XML for subscribing to a node.
     *
     * @param nodeId        the node identifier
     * @param subscriberJid the subscriber JID string
     * @return the IQ XML
     */
    public String generateSubscribeXml(String nodeId, String subscriberJid) {
        return "<iq type=\"set\" to=\"" + serviceJid.toBareJid() + "\">" +
                "<pubsub xmlns=\"" + NAMESPACE + "\">" +
                "<subscribe node=\"" + nodeId + "\" jid=\"" + subscriberJid + "\"/>" +
                "</pubsub></iq>";
    }

    private void notifySubscriber(String nodeId, PubSubItem item, PubSubSubscription sub) {
        LOG.debug("Notifying {} about item {} on node {}", sub.jid(), item.id(), nodeId);
        for (var listener : notificationListeners) {
            listener.accept(nodeId, item);
        }
    }
}
