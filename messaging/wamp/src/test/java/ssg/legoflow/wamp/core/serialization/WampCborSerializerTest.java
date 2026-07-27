package ssg.legoflow.wamp.core.serialization;

import ssg.legoflow.wamp.core.WampMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for WAMP message serialization/deserialization via CBOR.
 */
class WampCborSerializerTest {

    private final WampCborSerializer serializer = new WampCborSerializer();

    @Test
    void testRoundTripHello() {
        var msg = new WampMessage.Hello("realm1", Map.of("roles", Map.of()));
        var result = (WampMessage.Hello) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.realm()).isEqualTo("realm1");
    }

    @Test
    void testRoundTripWelcome() {
        var msg = new WampMessage.Welcome(99L, Map.of());
        var result = (WampMessage.Welcome) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.sessionId()).isEqualTo(99L);
    }

    @Test
    void testRoundTripPublish() {
        var msg = new WampMessage.Publish(1L, Map.of(), "topic", List.of("data"));
        var result = (WampMessage.Publish) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.topic()).isEqualTo("topic");
    }

    @Test
    void testRoundTripEvent() {
        var msg = new WampMessage.Event(1L, 2L, Map.of(), List.of("evt"));
        var result = (WampMessage.Event) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.subscriptionId()).isEqualTo(1L);
    }

    @Test
    void testRoundTripCall() {
        var msg = new WampMessage.Call(1L, Map.of(), "proc", List.of(1, 2));
        var result = (WampMessage.Call) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.procedure()).isEqualTo("proc");
    }

    @Test
    void testRoundTripCancel() {
        var msg = new WampMessage.Cancel(1L, Map.of("mode", "skip"));
        var result = (WampMessage.Cancel) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.requestId()).isEqualTo(1L);
    }

    @Test
    void testRoundTripChallenge() {
        var msg = new WampMessage.Challenge("ticket", Map.of());
        var result = (WampMessage.Challenge) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.authMethod()).isEqualTo("ticket");
    }

    @Test
    void testRoundTripInterrupt() {
        var msg = new WampMessage.Interrupt(5L, Map.of("mode", "kill"));
        var result = (WampMessage.Interrupt) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.requestId()).isEqualTo(5L);
    }

    @Test
    void testSerializeProducesCborData() {
        var msg = new WampMessage.Hello("realm1", Map.of());
        var bytes = serializer.serialize(msg);
        assertThat(bytes.length).isGreaterThan(0);
        // First byte should be a CBOR array header (major type 4)
        assertThat(bytes[0] & 0xe0).isEqualTo(0x80); // major type 4 = 0x80
    }
}
