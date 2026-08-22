package ssg.legoflow.wamp.demo.websocket;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class WsRpcDemoTest {

    @Test
    void testWsRpcDemoReturnsCorrectSum() {
        var demo = new WsRpcDemo();
        var result = demo.run();

        assertThat(result).hasSize(1);
        assertThat(((Number) result.getFirst()).intValue()).isEqualTo(8);
    }

    @Test
    void testWsRpcDemoRunsMultipleTimes() {
        var demo = new WsRpcDemo();

        var result1 = demo.run();
        var result2 = demo.run();

        assertThat(((Number) result1.getFirst()).intValue()).isEqualTo(8);
        assertThat(((Number) result2.getFirst()).intValue()).isEqualTo(8);
    }
}
