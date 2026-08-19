package ssg.legoflow.wamp.demo.websocket;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class FullWampServerDemoTest {

    @Test
    void testFullDemoTwoRealms() {
        var demo = new FullWampServerDemo();
        var result = demo.run();

        assertThat(result.realmCount()).isEqualTo(2);
    }

    @Test
    void testMathRealmRpcResult() {
        var demo = new FullWampServerDemo();
        var result = demo.run();

        var mathResult = result.rpcResults().get("realm.math");
        assertThat(mathResult).isNotNull();
        assertThat(((Number) mathResult.getFirst()).intValue()).isEqualTo(42);
    }

    @Test
    void testChatRealmPubSubEvents() {
        var demo = new FullWampServerDemo();
        var result = demo.run();

        var chatEvents = result.pubSubEvents().get("realm.chat");
        assertThat(chatEvents).isNotNull();
        assertThat(chatEvents).containsExactly("user1", "Hello everyone!");
    }

    @Test
    void testChatRealmRpcResult() {
        var demo = new FullWampServerDemo();
        var result = demo.run();

        var greetResult = result.rpcResults().get("realm.chat");
        assertThat(greetResult).isNotNull();
        assertThat(greetResult.getFirst()).isEqualTo("Welcome, Alice!");
    }

    @Test
    void testRealmIsolation() {
        var demo = new FullWampServerDemo();
        var result = demo.run();

        assertThat(result.rpcResults()).containsKeys("realm.math", "realm.chat");
        assertThat(result.pubSubEvents()).containsKey("realm.chat");
        assertThat(result.pubSubEvents()).doesNotContainKey("realm.math");
    }

    @Test
    void testDemoIsRepeatable() {
        var demo = new FullWampServerDemo();
        var result1 = demo.run();
        var result2 = demo.run();

        assertThat(result1.realmCount()).isEqualTo(result2.realmCount());
        assertThat(((Number) result1.rpcResults().get("realm.math").getFirst()).intValue())
                .isEqualTo(((Number) result2.rpcResults().get("realm.math").getFirst()).intValue());
    }
}
