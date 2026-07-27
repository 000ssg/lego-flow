package ssg.legoflow.xmpp.pubsub;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a Publish-Subscribe node (XEP-0060).
 *
 * <p>A PubSub node is a topic or channel to which items can be published and from
 * which items can be retrieved. Nodes can be either leaf nodes (contain items)
 * or collection nodes (contain other nodes).
 *
 * @since 1.0.0
 */
public class PubSubNode {

    /**
     * Node types.
     *
     * @since 1.0.0
     */
    public enum NodeType {
        /** Contains published items. */
        LEAF,
        /** Contains other nodes. */
        COLLECTION
    }

    /**
     * Access models for node subscriptions.
     *
     * @since 1.0.0
     */
    public enum AccessModel {
        /** Anyone can subscribe. */
        OPEN,
        /** Subscription requires approval. */
        AUTHORIZE,
        /** Only entities in a roster group can subscribe. */
        ROSTER,
        /** Only whitelisted entities can subscribe. */
        WHITELIST,
        /** Only the node owner can subscribe. */
        PRESENCE
    }

    private final String nodeId;
    private final NodeType nodeType;
    private final List<PubSubItem> items = new CopyOnWriteArrayList<>();
    private final List<PubSubSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private String title;
    private AccessModel accessModel;
    private int maxItems;

    /**
     * Creates a new PubSub node.
     *
     * @param nodeId   the node identifier
     * @param nodeType the node type
     */
    public PubSubNode(String nodeId, NodeType nodeType) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType must not be null");
        this.accessModel = AccessModel.OPEN;
        this.maxItems = 256;
    }

    /**
     * Creates a leaf node with the given identifier.
     *
     * @param nodeId the node identifier
     * @return a new leaf node
     */
    public static PubSubNode leaf(String nodeId) {
        return new PubSubNode(nodeId, NodeType.LEAF);
    }

    /**
     * Creates a collection node with the given identifier.
     *
     * @param nodeId the node identifier
     * @return a new collection node
     */
    public static PubSubNode collection(String nodeId) {
        return new PubSubNode(nodeId, NodeType.COLLECTION);
    }

    /**
     * Returns the node identifier.
     *
     * @return the node id
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Returns the node type.
     *
     * @return the node type
     */
    public NodeType getNodeType() {
        return nodeType;
    }

    /**
     * Returns the node title.
     *
     * @return the title, or null if not set
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the node title.
     *
     * @param title the title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the access model.
     *
     * @return the access model
     */
    public AccessModel getAccessModel() {
        return accessModel;
    }

    /**
     * Sets the access model.
     *
     * @param accessModel the access model
     */
    public void setAccessModel(AccessModel accessModel) {
        this.accessModel = Objects.requireNonNull(accessModel);
    }

    /**
     * Returns the maximum number of items.
     *
     * @return the max items
     */
    public int getMaxItems() {
        return maxItems;
    }

    /**
     * Sets the maximum number of items.
     *
     * @param maxItems the max items
     */
    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    /**
     * Publishes an item to this node.
     *
     * @param item the item to publish
     */
    public void publishItem(PubSubItem item) {
        Objects.requireNonNull(item, "item must not be null");
        items.add(item);
        // Trim to maxItems
        while (items.size() > maxItems) {
            items.removeFirst();
        }
    }

    /**
     * Removes an item by id.
     *
     * @param itemId the item id
     * @return true if the item was removed
     */
    public boolean retractItem(String itemId) {
        return items.removeIf(item -> item.id().equals(itemId));
    }

    /**
     * Returns all items.
     *
     * @return the list of items
     */
    public List<PubSubItem> getItems() {
        return List.copyOf(items);
    }

    /**
     * Returns the item with the given id.
     *
     * @param itemId the item id
     * @return the item, or null if not found
     */
    public PubSubItem getItem(String itemId) {
        return items.stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Adds a subscription.
     *
     * @param subscription the subscription
     */
    public void addSubscription(PubSubSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        subscriptions.add(subscription);
    }

    /**
     * Removes a subscription by subscriber JID string.
     *
     * @param subscriberJid the subscriber's bare JID string
     * @return true if a subscription was removed
     */
    public boolean removeSubscription(String subscriberJid) {
        return subscriptions.removeIf(sub -> sub.jid().equals(subscriberJid));
    }

    /**
     * Returns all subscriptions.
     *
     * @return the list of subscriptions
     */
    public List<PubSubSubscription> getSubscriptions() {
        return List.copyOf(subscriptions);
    }

    /**
     * Returns the subscription for the given subscriber.
     *
     * @param subscriberJid the subscriber's bare JID string
     * @return the subscription, or null if not subscribed
     */
    public PubSubSubscription getSubscription(String subscriberJid) {
        return subscriptions.stream()
                .filter(sub -> sub.jid().equals(subscriberJid))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the number of items.
     *
     * @return the item count
     */
    public int getItemCount() {
        return items.size();
    }
}
