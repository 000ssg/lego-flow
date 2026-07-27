package ssg.legoflow.wamp.core.serialization;

import ssg.legoflow.wamp.core.WampMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for WAMP message serialization/deserialization via MessagePack.
 */
class WampMessagePackSerializerTest {

    private final WampMessagePackSerializer serializer = new WampMessagePackSerializer();

    @Test
    void testRoundTripHello() {
        var msg = new WampMessage.Hello("realm1", Map.of("roles", Map.of("caller", Map.of())));
        var result = serializer.deserialize(serializer.serialize(msg));
        assertThat(result).isInstanceOf(WampMessage.Hello.class);
        var hello = (WampMessage.Hello) result;
        assertThat(hello.realm()).isEqualTo("realm1");
    }

    @Test
    void testRoundTripWelcome() {
        var msg = new WampMessage.Welcome(42L, Map.of("roles", Map.of()));
        var result = (WampMessage.Welcome) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.sessionId()).isEqualTo(42L);
    }

    @Test
    void testRoundTripAbort() {
        var msg = new WampMessage.Abort(Map.of(), "wamp.error.no_such_realm");
        var result = (WampMessage.Abort) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.reason()).isEqualTo("wamp.error.no_such_realm");
    }

    @Test
    void testRoundTripGoodbye() {
        var msg = new WampMessage.Goodbye(Map.of("message", "bye"), "wamp.close.normal");
        var result = (WampMessage.Goodbye) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.reason()).isEqualTo("wamp.close.normal");
    }

    @Test
    void testRoundTripChallenge() {
        var msg = new WampMessage.Challenge("wampcra", Map.of("challenge", "abc123"));
        var result = (WampMessage.Challenge) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.authMethod()).isEqualTo("wampcra");
    }

    @Test
    void testRoundTripAuthenticate() {
        var msg = new WampMessage.Authenticate("signature123", Map.of());
        var result = (WampMessage.Authenticate) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.signature()).isEqualTo("signature123");
    }

    @Test
    void testRoundTripPublish() {
        var msg = new WampMessage.Publish(1L, Map.of(), "com.test.topic", List.of("data", 42));
        var result = (WampMessage.Publish) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.topic()).isEqualTo("com.test.topic");
        assertThat(result.args()).hasSize(2);
    }

    @Test
    void testRoundTripPublishNoArgs() {
        var msg = new WampMessage.Publish(1L, Map.of(), "com.test.topic", List.of());
        var result = (WampMessage.Publish) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.args()).isEmpty();
    }

    @Test
    void testRoundTripSubscribe() {
        var msg = new WampMessage.Subscribe(5L, Map.of("match", "prefix"), "com.test.");
        var result = (WampMessage.Subscribe) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.topic()).isEqualTo("com.test.");
    }

    @Test
    void testRoundTripEvent() {
        var msg = new WampMessage.Event(100L, 200L, Map.of("publisher", 5L), List.of("evt"));
        var result = (WampMessage.Event) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.subscriptionId()).isEqualTo(100L);
        assertThat(result.publicationId()).isEqualTo(200L);
    }

    @Test
    void testRoundTripCall() {
        var msg = new WampMessage.Call(10L, Map.of(), "com.test.proc", List.of(1, 2));
        var result = (WampMessage.Call) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.procedure()).isEqualTo("com.test.proc");
    }

    @Test
    void testRoundTripCancel() {
        var msg = new WampMessage.Cancel(10L, Map.of("mode", "kill"));
        var result = (WampMessage.Cancel) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.requestId()).isEqualTo(10L);
    }

    @Test
    void testRoundTripResult() {
        var msg = new WampMessage.Result(10L, Map.of("progress", true), List.of("partial"));
        var result = (WampMessage.Result) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.args()).containsExactly("partial");
    }

    @Test
    void testRoundTripInvocation() {
        var msg = new WampMessage.Invocation(5L, 10L, Map.of("caller", 99L), List.of("a"));
        var result = (WampMessage.Invocation) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.registrationId()).isEqualTo(10L);
    }

    @Test
    void testRoundTripInterrupt() {
        var msg = new WampMessage.Interrupt(5L, Map.of("mode", "killnowait"));
        var result = (WampMessage.Interrupt) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.requestId()).isEqualTo(5L);
    }

    @Test
    void testRoundTripYield() {
        var msg = new WampMessage.Yield(5L, Map.of(), List.of("result"));
        var result = (WampMessage.Yield) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.args()).containsExactly("result");
    }

    @Test
    void testRoundTripError() {
        var msg = new WampMessage.Error(48, 10L, Map.of(), "wamp.error.runtime");
        var result = (WampMessage.Error) serializer.deserialize(serializer.serialize(msg));
        assertThat(result.error()).isEqualTo("wamp.error.runtime");
        assertThat(result.requestType()).isEqualTo(48);
    }

    @Test
    void testSerializeProducesBinaryData() {
        var msg = new WampMessage.Hello("realm1", Map.of());
        var bytes = serializer.serialize(msg);
        assertThat(bytes.length).isGreaterThan(0);
        // First byte should be a MessagePack array header (fixarray with type code + 2 fields = 3)
        assertThat(bytes[0] & 0xf0).isEqualTo(0x90); // fixarray
    }
}
