package ssg.legoflow.messaging.nats.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link NatsStatus}.
 */
class NatsStatusTest {

    @Test
    void testFromCode() {
        assertThat(NatsStatus.fromCode(100)).isEqualTo(NatsStatus.IDLE_HEARTBEAT);
        assertThat(NatsStatus.fromCode(404)).isEqualTo(NatsStatus.NO_MESSAGES);
        assertThat(NatsStatus.fromCode(408)).isEqualTo(NatsStatus.REQUEST_TIMEOUT);
        assertThat(NatsStatus.fromCode(409)).isEqualTo(NatsStatus.CONFLICT);
        assertThat(NatsStatus.fromCode(503)).isEqualTo(NatsStatus.NO_RESPONDERS);
    }

    @Test
    void testFromCodeUnknown() {
        assertThat(NatsStatus.fromCode(999)).isNull();
    }

    @Test
    void testIsError() {
        assertThat(NatsStatus.IDLE_HEARTBEAT.isError()).isFalse();
        assertThat(NatsStatus.NO_MESSAGES.isError()).isTrue();
        assertThat(NatsStatus.REQUEST_TIMEOUT.isError()).isTrue();
        assertThat(NatsStatus.CONFLICT.isError()).isTrue();
        assertThat(NatsStatus.NO_RESPONDERS.isError()).isTrue();
    }

    @Test
    void testCode() {
        assertThat(NatsStatus.IDLE_HEARTBEAT.code()).isEqualTo(100);
        assertThat(NatsStatus.NO_MESSAGES.code()).isEqualTo(404);
    }

    @Test
    void testDescription() {
        assertThat(NatsStatus.IDLE_HEARTBEAT.description()).isEqualTo("Idle Heartbeat");
        assertThat(NatsStatus.NO_RESPONDERS.description()).isEqualTo("No Responders");
    }
}
