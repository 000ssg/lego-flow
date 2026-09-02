package ssg.legoflow.xmpp.pubsub;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link PubSubManager}.
 *
 * @since 0.1.0
 */
class PubSubManagerTest {

    private PubSubManager manager;

    @BeforeEach
    void setUp() {
        manager = new PubSubManager(JID.parse("pubsub.example.com"));
    }

    @Test
    void testCreateNode() {
        var node = manager.createNode("news");
        assertThat(node).isNotNull();
        assertThat(node.getNodeId()).isEqualTo("news");
        assertThat(manager.getNode("news")).isSameAs(node);
    }

    @Test
    void testCreateDuplicateNodeThrows() {
        manager.createNode("news");
        assertThatThrownBy(() -> manager.createNode("news"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void testCreateNodeWithType() {
        var node = manager.createNode("collection-1", PubSubNode.NodeType.COLLECTION);
        assertThat(node.getNodeType()).isEqualTo(PubSubNode.NodeType.COLLECTION);
    }

    @Test
    void testDeleteNode() {
        manager.createNode("temp");
        assertThat(manager.deleteNode("temp")).isTrue();
        assertThat(manager.getNode("temp")).isNull();
    }

    @Test
    void testDeleteNonexistentNode() {
        assertThat(manager.deleteNode("ghost")).isFalse();
    }

    @Test
    void testPublish() {
        manager.createNode("news");
        var item = manager.publish("news", "<entry>headline</entry>", "alice@example.com");

        assertThat(item).isNotNull();
        assertThat(item.payload()).isEqualTo("<entry>headline</entry>");
        assertThat(item.publisher()).isEqualTo("alice@example.com");

        var node = manager.getNode("news");
        assertThat(node.getItems()).hasSize(1);
    }

    @Test
    void testPublishWithSpecificId() {
        manager.createNode("news");
        var item = manager.publish("news", "custom-id", "<data/>", "alice@example.com");

        assertThat(item.id()).isEqualTo("custom-id");
    }

    @Test
    void testPublishToNonexistentNodeThrows() {
        assertThatThrownBy(() -> manager.publish("ghost", "<data/>", "alice@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testRetract() {
        manager.createNode("news");
        var item = manager.publish("news", "item-1", "<data/>", "alice@example.com");

        assertThat(manager.retract("news", "item-1")).isTrue();
        assertThat(manager.getNode("news").getItems()).isEmpty();
    }

    @Test
    void testSubscribe() {
        manager.createNode("news");
        var sub = manager.subscribe("news", "bob@example.com");

        assertThat(sub).isNotNull();
        assertThat(sub.state()).isEqualTo(PubSubSubscription.State.SUBSCRIBED);
        assertThat(sub.nodeId()).isEqualTo("news");
    }

    @Test
    void testSubscribeIdempotent() {
        manager.createNode("news");
        var sub1 = manager.subscribe("news", "bob@example.com");
        var sub2 = manager.subscribe("news", "bob@example.com");

        assertThat(sub2).isSameAs(sub1);
    }

    @Test
    void testSubscribeToNonexistentNodeThrows() {
        assertThatThrownBy(() -> manager.subscribe("ghost", "bob@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnsubscribe() {
        manager.createNode("news");
        manager.subscribe("news", "bob@example.com");

        assertThat(manager.unsubscribe("news", "bob@example.com")).isTrue();
        assertThat(manager.getNode("news").getSubscriptions()).isEmpty();
    }

    @Test
    void testUnsubscribeNonexistent() {
        assertThat(manager.unsubscribe("ghost", "bob@example.com")).isFalse();
    }

    @Test
    void testNotifications() {
        manager.createNode("news");
        manager.subscribe("news", "bob@example.com");

        var notifications = new ArrayList<PubSubItem>();
        manager.addNotificationListener((nodeId, item) -> notifications.add(item));

        manager.publish("news", "<data/>", "alice@example.com");
        assertThat(notifications).hasSize(1);
    }

    @Test
    void testNoNotificationWithoutSubscription() {
        manager.createNode("news");
        var notifications = new ArrayList<PubSubItem>();
        manager.addNotificationListener((nodeId, item) -> notifications.add(item));

        manager.publish("news", "<data/>", "alice@example.com");
        assertThat(notifications).isEmpty();
    }

    @Test
    void testGetNodes() {
        manager.createNode("a");
        manager.createNode("b");
        assertThat(manager.getNodes()).hasSize(2);
    }

    @Test
    void testGenerateCreateNodeXml() {
        String xml = manager.generateCreateNodeXml("my-node");
        assertThat(xml).contains("type=\"set\"");
        assertThat(xml).contains("node=\"my-node\"");
        assertThat(xml).contains(PubSubManager.NAMESPACE);
    }

    @Test
    void testGenerateDeleteNodeXml() {
        String xml = manager.generateDeleteNodeXml("my-node");
        assertThat(xml).contains("<delete node=\"my-node\"");
        assertThat(xml).contains(PubSubManager.OWNER_NAMESPACE);
    }

    @Test
    void testGenerateSubscribeXml() {
        String xml = manager.generateSubscribeXml("news", "bob@example.com");
        assertThat(xml).contains("node=\"news\"");
        assertThat(xml).contains("jid=\"bob@example.com\"");
    }
}
