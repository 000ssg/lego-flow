package ssg.legoflow.email.common.header;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MessageId}.
 */
class MessageIdTest {

    @Test
    void testParseSingle() {
        MessageId id = MessageId.parse("<abc123@example.com>");
        assertThat(id).isNotNull();
        assertThat(id.id()).isEqualTo("abc123@example.com");
    }

    @Test
    void testParseWithoutBrackets() {
        MessageId id = MessageId.parse("abc123@example.com");
        assertThat(id).isNotNull();
        assertThat(id.id()).isEqualTo("abc123@example.com");
    }

    @Test
    void testParseNull() {
        assertThat(MessageId.parse(null)).isNull();
        assertThat(MessageId.parse("")).isNull();
    }

    @Test
    void testParseList() {
        List<MessageId> ids = MessageId.parseList(
                "<id1@a.com> <id2@b.com> <id3@c.com>");
        assertThat(ids).hasSize(3);
        assertThat(ids.get(0).id()).isEqualTo("id1@a.com");
        assertThat(ids.get(1).id()).isEqualTo("id2@b.com");
        assertThat(ids.get(2).id()).isEqualTo("id3@c.com");
    }

    @Test
    void testParseListEmpty() {
        assertThat(MessageId.parseList(null)).isEmpty();
        assertThat(MessageId.parseList("")).isEmpty();
    }

    @Test
    void testToWireFormat() {
        MessageId id = new MessageId("abc123@example.com");
        assertThat(id.toWireFormat()).isEqualTo("<abc123@example.com>");
    }

    @Test
    void testSerializeList() {
        List<MessageId> ids = List.of(
                new MessageId("id1@a.com"),
                new MessageId("id2@b.com")
        );
        String serialized = MessageId.serializeList(ids);
        assertThat(serialized).isEqualTo("<id1@a.com> <id2@b.com>");
    }

    @Test
    void testGenerate() {
        MessageId id = MessageId.generate("example.com");
        assertThat(id.id()).endsWith("@example.com");
        assertThat(id.id()).contains("-"); // UUID contains dashes
    }

    @Test
    void testEquality() {
        MessageId a = new MessageId("abc@example.com");
        MessageId b = new MessageId("abc@example.com");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testToString() {
        MessageId id = new MessageId("abc@example.com");
        assertThat(id.toString()).isEqualTo("<abc@example.com>");
    }
}
