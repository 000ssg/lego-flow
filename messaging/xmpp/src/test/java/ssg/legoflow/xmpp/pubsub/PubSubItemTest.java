package ssg.legoflow.xmpp.pubsub;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PubSubItem}.
 *
 * @since 1.0.0
 */
class PubSubItemTest {

    @Test
    void testCreateItem() {
        var item = new PubSubItem("item-1", "<data>hello</data>", "alice@example.com", Instant.now());

        assertThat(item.id()).isEqualTo("item-1");
        assertThat(item.payload()).isEqualTo("<data>hello</data>");
        assertThat(item.publisher()).isEqualTo("alice@example.com");
        assertThat(item.timestamp()).isNotNull();
    }

    @Test
    void testCreateItemWithNullPayload() {
        var item = new PubSubItem("item-2", null, null, Instant.now());
        assertThat(item.payload()).isNull();
        assertThat(item.publisher()).isNull();
    }

    @Test
    void testToXmlWithPayload() {
        var item = new PubSubItem("item-1", "<entry>data</entry>", "alice@example.com", Instant.now());
        String xml = item.toXml();

        assertThat(xml).contains("id=\"item-1\"");
        assertThat(xml).contains("publisher=\"alice@example.com\"");
        assertThat(xml).contains("<entry>data</entry>");
        assertThat(xml).endsWith("</item>");
    }

    @Test
    void testToXmlWithoutPayload() {
        var item = new PubSubItem("item-2", null, null, Instant.now());
        String xml = item.toXml();

        assertThat(xml).contains("id=\"item-2\"");
        assertThat(xml).endsWith("/>");
    }

    @Test
    void testNullIdThrows() {
        assertThatThrownBy(() -> new PubSubItem(null, null, null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
