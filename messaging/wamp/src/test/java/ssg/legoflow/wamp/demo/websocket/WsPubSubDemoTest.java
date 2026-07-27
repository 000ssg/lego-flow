package ssg.legoflow.wamp.demo.websocket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WsPubSubDemoTest {

    @Test
    void testBothSubscribersReceiveEvents() {
        var demo = new WsPubSubDemo();
        var result = demo.run();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly("ws-hello", 99);
        assertThat(result.get(1)).containsExactly("ws-hello", 99);
    }

    @Test
    void testDemoIsRepeatable() {
        var demo = new WsPubSubDemo();

        var result1 = demo.run();
        var result2 = demo.run();

        assertThat(result1.get(0)).containsExactly("ws-hello", 99);
        assertThat(result2.get(0)).containsExactly("ws-hello", 99);
    }
}
