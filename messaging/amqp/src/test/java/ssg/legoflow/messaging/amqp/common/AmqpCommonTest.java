package ssg.legoflow.messaging.amqp.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AmqpCommonTest {
    @Test void testConnectionStates() {
        var states = ConnectionState.values();
        assertThat(states).contains(ConnectionState.START, ConnectionState.OPENED);
    }
    @Test void testAmqpConstantsDefaults() {
        assertThat(AmqpConstants.DEFAULT_PORT).isEqualTo(5672);
        assertThat(AmqpConstants.DEFAULT_MAX_FRAME_SIZE).isEqualTo(65536);
    }
}
