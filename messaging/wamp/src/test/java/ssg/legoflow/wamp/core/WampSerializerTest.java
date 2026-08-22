package ssg.legoflow.wamp.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class WampSerializerTest {

    private final WampSerializer serializer = new WampSerializer();

    @Test
    void testSerializeDeserializeHello() {
        var hello = new WampMessage.Hello("realm1", Map.of("roles", Map.of()));
        var json = serializer.serialize(hello);
        var deserialized = serializer.deserialize(json);

        assertThat(deserialized).isInstanceOf(WampMessage.Hello.class);
        var result = (WampMessage.Hello) deserialized;
        assertThat(result.realm()).isEqualTo("realm1");
        assertThat(result.details()).containsKey("roles");
    }

    @Test
    void testSerializeDeserializeCall() {
        var call = new WampMessage.Call(7L, Map.of(), "com.example.add", List.of(3, 5));
        var json = serializer.serialize(call);
        var deserialized = serializer.deserialize(json);

        assertThat(deserialized).isInstanceOf(WampMessage.Call.class);
        var result = (WampMessage.Call) deserialized;
        assertThat(result.requestId()).isEqualTo(7L);
        assertThat(result.procedure()).isEqualTo("com.example.add");
        assertThat(result.args()).hasSize(2);
    }

    @Test
    void testSerializeDeserializeResult() {
        var resultMsg = new WampMessage.Result(7L, Map.of(), List.of(8));
        var json = serializer.serialize(resultMsg);
        var deserialized = serializer.deserialize(json);

        assertThat(deserialized).isInstanceOf(WampMessage.Result.class);
        var result = (WampMessage.Result) deserialized;
        assertThat(result.requestId()).isEqualTo(7L);
        assertThat(result.args()).hasSize(1);
        assertThat(((Number) result.args().getFirst()).intValue()).isEqualTo(8);
    }

    @Test
    void testSerializeDeserializeSubscribe() {
        var subscribe = new WampMessage.Subscribe(3L, Map.of(), "topic.events");
        var json = serializer.serialize(subscribe);
        var deserialized = serializer.deserialize(json);

        assertThat(deserialized).isInstanceOf(WampMessage.Subscribe.class);
        var result = (WampMessage.Subscribe) deserialized;
        assertThat(result.requestId()).isEqualTo(3L);
        assertThat(result.topic()).isEqualTo("topic.events");
    }

    @Test
    void testSerializeDeserializePublish() {
        var publish = new WampMessage.Publish(5L, Map.of(), "topic.test", List.of("hello", 42));
        var json = serializer.serialize(publish);
        var deserialized = serializer.deserialize(json);

        assertThat(deserialized).isInstanceOf(WampMessage.Publish.class);
        var result = (WampMessage.Publish) deserialized;
        assertThat(result.requestId()).isEqualTo(5L);
        assertThat(result.topic()).isEqualTo("topic.test");
        assertThat(result.args()).hasSize(2);
        assertThat(result.args().getFirst()).isEqualTo("hello");
    }
}
