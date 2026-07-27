package ssg.legoflow.messaging.kafka.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NodeTest {

    @Test
    void testCreation() {
        var node = new Node(1, "broker1.example.com", 9092);
        assertThat(node.id()).isEqualTo(1);
        assertThat(node.host()).isEqualTo("broker1.example.com");
        assertThat(node.port()).isEqualTo(9092);
    }

    @Test
    void testNullHostRejected() {
        assertThatThrownBy(() -> new Node(0, null, 9092))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEquality() {
        assertThat(new Node(1, "h", 9092)).isEqualTo(new Node(1, "h", 9092));
    }

    @Test
    void testToString() {
        assertThat(new Node(0, "localhost", 9092).toString()).isEqualTo("0@localhost:9092");
    }
}
