package ssg.legoflow.xmpp.pubsub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link PubSubNode}.
 *
 * @since 0.1.0
 */
class PubSubNodeTest {

    private PubSubNode node;

    @BeforeEach
    void setUp() {
        node = PubSubNode.leaf("test-node");
    }

    @Test
    void testCreateLeafNode() {
        assertThat(node.getNodeId()).isEqualTo("test-node");
        assertThat(node.getNodeType()).isEqualTo(PubSubNode.NodeType.LEAF);
        assertThat(node.getAccessModel()).isEqualTo(PubSubNode.AccessModel.OPEN);
        assertThat(node.getItems()).isEmpty();
    }

    @Test
    void testCreateCollectionNode() {
        var collection = PubSubNode.collection("coll-1");
        assertThat(collection.getNodeType()).isEqualTo(PubSubNode.NodeType.COLLECTION);
    }

    @Test
    void testPublishItem() {
        var item = new PubSubItem("item-1", "<data>value</data>", "alice@example.com", Instant.now());
        node.publishItem(item);

        assertThat(node.getItems()).hasSize(1);
        assertThat(node.getItem("item-1")).isNotNull();
        assertThat(node.getItemCount()).isEqualTo(1);
    }

    @Test
    void testRetractItem() {
        var item = new PubSubItem("item-1", "<data/>", "alice@example.com", Instant.now());
        node.publishItem(item);

        boolean retracted = node.retractItem("item-1");
        assertThat(retracted).isTrue();
        assertThat(node.getItems()).isEmpty();
    }

    @Test
    void testRetractNonexistentItem() {
        assertThat(node.retractItem("nope")).isFalse();
    }

    @Test
    void testMaxItems() {
        node.setMaxItems(3);
        for (int i = 0; i < 5; i++) {
            node.publishItem(new PubSubItem("item-" + i, null, null, Instant.now()));
        }
        assertThat(node.getItems()).hasSize(3);
        // Oldest items should be trimmed
        assertThat(node.getItem("item-0")).isNull();
        assertThat(node.getItem("item-1")).isNull();
        assertThat(node.getItem("item-2")).isNotNull();
    }

    @Test
    void testSubscription() {
        var sub = new PubSubSubscription("test-node", "alice@example.com",
                "sub-1", PubSubSubscription.State.SUBSCRIBED);
        node.addSubscription(sub);

        assertThat(node.getSubscriptions()).hasSize(1);
        assertThat(node.getSubscription("alice@example.com")).isNotNull();
    }

    @Test
    void testRemoveSubscription() {
        var sub = new PubSubSubscription("test-node", "alice@example.com",
                "sub-1", PubSubSubscription.State.SUBSCRIBED);
        node.addSubscription(sub);

        boolean removed = node.removeSubscription("alice@example.com");
        assertThat(removed).isTrue();
        assertThat(node.getSubscriptions()).isEmpty();
    }

    @Test
    void testTitleAndAccessModel() {
        node.setTitle("My Node");
        assertThat(node.getTitle()).isEqualTo("My Node");

        node.setAccessModel(PubSubNode.AccessModel.WHITELIST);
        assertThat(node.getAccessModel()).isEqualTo(PubSubNode.AccessModel.WHITELIST);
    }
}
