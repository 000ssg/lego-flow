package ssg.legoflow.xmpp.pubsub;

import java.util.Objects;

/**
 * A subscription to a PubSub node (XEP-0060).
 *
 * @param nodeId       the node identifier
 * @param jid          the subscriber's bare JID string
 * @param subscriptionId the subscription identifier
 * @param state        the subscription state
 * @since 1.0.0
 */
public record PubSubSubscription(String nodeId, String jid, String subscriptionId, State state) {

    /**
     * Subscription states.
     *
     * @since 1.0.0
     */
    public enum State {
        /** Subscription is pending approval. */
        PENDING,
        /** Subscription is active. */
        SUBSCRIBED,
        /** Subscription has been revoked or not yet created. */
        NONE,
        /** Entity cannot subscribe (e.g., not authorized). */
        UNCONFIGURED
    }

    /**
     * Constructs a validated PubSub subscription.
     */
    public PubSubSubscription {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(jid, "jid must not be null");
        Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }

    /**
     * Serializes this subscription to XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        return "<subscription node=\"" + nodeId + "\"" +
                " jid=\"" + jid + "\"" +
                " subid=\"" + subscriptionId + "\"" +
                " subscription=\"" + state.name().toLowerCase() + "\"/>";
    }
}
