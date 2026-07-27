package ssg.legoflow.messaging.amqp.message;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp.types.AmqpType;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MessageCodec} — AMQP message encoding/decoding.
 */
class MessageCodecTest {

    @Test void testEmptyMessage() {
        var msg = new AmqpMessage();
        ByteBuffer buf = MessageCodec.encode(msg);
        assertThat(buf.remaining()).isEqualTo(0); // No sections to encode
    }

    @Test void testStringBodyRoundTrip() {
        var msg = AmqpMessage.of("Hello, AMQP!");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.bodyAsString()).isEqualTo("Hello, AMQP!");
    }

    @Test void testBinaryBodyRoundTrip() {
        byte[] data = {1, 2, 3, 4, 5};
        var msg = AmqpMessage.of(data);
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.bodyAsBytes()).isEqualTo(data);
        assertThat(decoded.bodyType()).isEqualTo(AmqpMessage.BodyType.DATA);
    }

    @Test void testHeaderRoundTrip() {
        var msg = new AmqpMessage()
                .header(new Header(true, (short) 7, 60000, true, 3))
                .bodyString("test");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.header()).isNotNull();
        assertThat(decoded.header().durable()).isTrue();
        assertThat(decoded.header().priority()).isEqualTo((short) 7);
        assertThat(decoded.header().ttl()).isEqualTo(60000);
        assertThat(decoded.header().firstAcquirer()).isTrue();
        assertThat(decoded.header().deliveryCount()).isEqualTo(3);
    }

    @Test void testHeaderDefaults() {
        var msg = new AmqpMessage()
                .header(new Header())
                .bodyString("test");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.header().durable()).isFalse();
        assertThat(decoded.header().priority()).isEqualTo((short) 4);
        assertThat(decoded.header().firstAcquirer()).isFalse();
    }

    @Test void testDurableHeader() {
        var msg = new AmqpMessage()
                .header(Header.ofDurable())
                .bodyString("durable");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.header().durable()).isTrue();
    }

    @Test void testPropertiesRoundTrip() {
        var props = Properties.builder()
                .messageId("msg-001")
                .to("/queue/orders")
                .subject("order")
                .replyTo("/queue/replies")
                .correlationId("corr-001")
                .contentType("application/json")
                .contentEncoding("gzip")
                .creationTime(1234567890L)
                .groupId("group-1")
                .groupSequence(5)
                .replyToGroupId("reply-group")
                .build();

        var msg = new AmqpMessage().properties(props).bodyString("test");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);

        assertThat(decoded.properties()).isNotNull();
        assertThat(decoded.properties().messageId()).isEqualTo("msg-001");
        assertThat(decoded.properties().to()).isEqualTo("/queue/orders");
        assertThat(decoded.properties().subject()).isEqualTo("order");
        assertThat(decoded.properties().replyTo()).isEqualTo("/queue/replies");
        assertThat(decoded.properties().correlationId()).isEqualTo("corr-001");
        assertThat(decoded.properties().contentType()).isEqualTo("application/json");
        assertThat(decoded.properties().contentEncoding()).isEqualTo("gzip");
        assertThat(decoded.properties().creationTime()).isEqualTo(1234567890L);
        assertThat(decoded.properties().groupId()).isEqualTo("group-1");
        assertThat(decoded.properties().groupSequence()).isEqualTo(5);
        assertThat(decoded.properties().replyToGroupId()).isEqualTo("reply-group");
    }

    @Test void testEmptyProperties() {
        var msg = new AmqpMessage().properties(new Properties()).bodyString("test");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.properties()).isNotNull();
        assertThat(decoded.properties().messageId()).isNull();
    }

    @Test void testApplicationPropertiesRoundTrip() {
        var msg = new AmqpMessage()
                .applicationProperties(Map.of("key1", "value1", "count", 42))
                .bodyString("test");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.applicationProperties()).isNotEmpty();
        assertThat(decoded.applicationProperties().get("key1")).isEqualTo("value1");
        assertThat(decoded.applicationProperties().get("count")).isEqualTo(42);
    }

    @Test void testMessageAnnotationsRoundTrip() {
        var msg = new AmqpMessage()
                .messageAnnotations(Map.of("x-annotation", "annotated"))
                .bodyString("test");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.messageAnnotations()).containsEntry("x-annotation", "annotated");
    }

    @Test void testDeliveryAnnotationsRoundTrip() {
        var msg = new AmqpMessage()
                .deliveryAnnotations(Map.of("x-delivery", "hop1"))
                .bodyString("test");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.deliveryAnnotations()).containsEntry("x-delivery", "hop1");
    }

    @Test void testFooterRoundTrip() {
        var msg = new AmqpMessage()
                .bodyString("test")
                .footer(Map.of("checksum", "abc123"));
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.footer()).containsEntry("checksum", "abc123");
    }

    @Test void testAmqpValueBodyRoundTrip() {
        var msg = new AmqpMessage().bodyValue(new AmqpType.Int(42));
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.body()).isEqualTo(new AmqpType.Int(42));
        assertThat(decoded.bodyType()).isEqualTo(AmqpMessage.BodyType.AMQP_VALUE);
    }

    @Test void testAmqpSequenceBodyRoundTrip() {
        var msg = new AmqpMessage().bodySequence(List.of(
                new AmqpType.AmqpString("a"),
                new AmqpType.AmqpString("b"),
                new AmqpType.AmqpString("c")
        ));
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.bodyType()).isEqualTo(AmqpMessage.BodyType.AMQP_SEQUENCE);
    }

    @Test void testFullMessageRoundTrip() {
        var msg = new AmqpMessage()
                .header(new Header(true, (short) 5, 30000, false, 0))
                .deliveryAnnotations(Map.of("x-hop", "value"))
                .messageAnnotations(Map.of("x-msg", "ann"))
                .properties(Properties.builder()
                        .messageId("full-1")
                        .to("/topic/events")
                        .correlationId("corr-full")
                        .contentType("text/plain")
                        .build())
                .applicationProperties(Map.of("app-key", "app-val"))
                .bodyString("Full message body")
                .footer(Map.of("x-footer", "foot"));

        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);

        assertThat(decoded.header().durable()).isTrue();
        assertThat(decoded.header().priority()).isEqualTo((short) 5);
        assertThat(decoded.deliveryAnnotations()).containsKey("x-hop");
        assertThat(decoded.messageAnnotations()).containsKey("x-msg");
        assertThat(decoded.properties().messageId()).isEqualTo("full-1");
        assertThat(decoded.properties().to()).isEqualTo("/topic/events");
        assertThat(decoded.applicationProperties()).containsKey("app-key");
        assertThat(decoded.bodyAsString()).isEqualTo("Full message body");
        assertThat(decoded.footer()).containsKey("x-footer");
    }

    @Test void testMessageOfString() {
        var msg = AmqpMessage.of("simple");
        assertThat(msg.bodyAsString()).isEqualTo("simple");
    }

    @Test void testMessageOfBytes() {
        var msg = AmqpMessage.of(new byte[]{1, 2});
        assertThat(msg.bodyAsBytes()).isEqualTo(new byte[]{1, 2});
    }

    @Test void testMessageOfPropertiesAndString() {
        var msg = AmqpMessage.of(Properties.builder().messageId("x").build(), "body");
        assertThat(msg.properties().messageId()).isEqualTo("x");
        assertThat(msg.bodyAsString()).isEqualTo("body");
    }

    @Test void testPropertiesBuilder() {
        var props = Properties.builder()
                .messageId("id")
                .userId("user".getBytes())
                .to("dest")
                .absoluteExpiryTime(999)
                .build();
        assertThat(props.messageId()).isEqualTo("id");
        assertThat(props.userId()).isEqualTo("user".getBytes());
        assertThat(props.to()).isEqualTo("dest");
        assertThat(props.absoluteExpiryTime()).isEqualTo(999);
    }

    @Test void testPropertiesUserIdRoundTrip() {
        byte[] userId = "admin".getBytes();
        var msg = new AmqpMessage()
                .properties(Properties.builder().userId(userId).build())
                .bodyString("test");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.properties().userId()).isEqualTo(userId);
    }

    @Test void testApplicationPropertiesMultipleTypes() {
        var msg = new AmqpMessage()
                .applicationProperties(Map.of(
                        "string", "val",
                        "integer", 42,
                        "bool", true,
                        "long", 999L
                ))
                .bodyString("test");
        ByteBuffer buf = MessageCodec.encode(msg);
        buf.rewind();
        var decoded = MessageCodec.decode(buf);
        assertThat(decoded.applicationProperties()).hasSize(4);
    }
}
