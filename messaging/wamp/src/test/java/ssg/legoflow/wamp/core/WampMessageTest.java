package ssg.legoflow.wamp.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class WampMessageTest {

    @Test
    void testHelloMessage() {
        var hello = new WampMessage.Hello("realm1", Map.of("roles", Map.of("caller", Map.of())));

        assertThat(hello.type()).isEqualTo(WampMessageType.HELLO);
        assertThat(hello.realm()).isEqualTo("realm1");
        assertThat(hello.details()).containsKey("roles");
    }

    @Test
    void testWelcomeMessage() {
        var welcome = new WampMessage.Welcome(12345L, Map.of("roles", Map.of("broker", Map.of())));

        assertThat(welcome.type()).isEqualTo(WampMessageType.WELCOME);
        assertThat(welcome.sessionId()).isEqualTo(12345L);
        assertThat(welcome.details()).containsKey("roles");
    }

    @Test
    void testCallAndResultMessages() {
        var call = new WampMessage.Call(1L, Map.of(), "com.example.add", List.of(3, 5));
        var result = new WampMessage.Result(1L, Map.of(), List.of(8));

        assertThat(call.type()).isEqualTo(WampMessageType.CALL);
        assertThat(call.procedure()).isEqualTo("com.example.add");
        assertThat(call.args()).containsExactly(3, 5);
        assertThat(result.type()).isEqualTo(WampMessageType.RESULT);
        assertThat(result.args()).containsExactly(8);
    }

    @Test
    void testPublishAndSubscribeMessages() {
        var publish = new WampMessage.Publish(1L, Map.of(), "topic.test", List.of("data"));
        var subscribe = new WampMessage.Subscribe(2L, Map.of(), "topic.test");
        var subscribed = new WampMessage.Subscribed(2L, 100L);

        assertThat(publish.type()).isEqualTo(WampMessageType.PUBLISH);
        assertThat(publish.topic()).isEqualTo("topic.test");
        assertThat(subscribe.type()).isEqualTo(WampMessageType.SUBSCRIBE);
        assertThat(subscribed.subscriptionId()).isEqualTo(100L);
    }

    @Test
    void testErrorMessage() {
        var error = new WampMessage.Error(48, 1L, Map.of(), "wamp.error.no_such_procedure");

        assertThat(error.type()).isEqualTo(WampMessageType.ERROR);
        assertThat(error.requestType()).isEqualTo(48);
        assertThat(error.error()).isEqualTo("wamp.error.no_such_procedure");
    }

    @Test
    void testSealedInterfacePermitsAllTypes() {
        // Verify that all message types implement WampMessage
        WampMessage[] messages = {
                new WampMessage.Hello("r", Map.of()),
                new WampMessage.Welcome(1, Map.of()),
                new WampMessage.Abort(Map.of(), "reason"),
                new WampMessage.Goodbye(Map.of(), "reason"),
                new WampMessage.Error(1, 1, Map.of(), "err"),
                new WampMessage.Publish(1, Map.of(), "t", List.of()),
                new WampMessage.Published(1, 1),
                new WampMessage.Subscribe(1, Map.of(), "t"),
                new WampMessage.Subscribed(1, 1),
                new WampMessage.Unsubscribe(1, 1),
                new WampMessage.Unsubscribed(1),
                new WampMessage.Call(1, Map.of(), "p", List.of()),
                new WampMessage.Result(1, Map.of(), List.of()),
                new WampMessage.Register(1, Map.of(), "p"),
                new WampMessage.Registered(1, 1),
                new WampMessage.Unregister(1, 1),
                new WampMessage.Unregistered(1),
                new WampMessage.Invocation(1, 1, Map.of(), List.of()),
                new WampMessage.Yield(1, Map.of(), List.of())
        };

        assertThat(messages).hasSize(19);
        for (var msg : messages) {
            assertThat(msg).isInstanceOf(WampMessage.class);
            assertThat(msg.type()).isNotNull();
        }
    }
}
