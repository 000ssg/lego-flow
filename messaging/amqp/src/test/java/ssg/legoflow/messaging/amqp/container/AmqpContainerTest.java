package ssg.legoflow.messaging.amqp.container;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AmqpContainerTest {
    @Test void testContainerRejectsNullConfig() {
        assertThatThrownBy(() -> new AmqpContainer(null))
                .isInstanceOf(NullPointerException.class);
    }
}
