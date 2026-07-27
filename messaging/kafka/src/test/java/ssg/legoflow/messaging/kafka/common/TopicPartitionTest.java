package ssg.legoflow.messaging.kafka.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TopicPartitionTest {

    @Test
    void testCreation() {
        var tp = new TopicPartition("test", 0);
        assertThat(tp.topic()).isEqualTo("test");
        assertThat(tp.partition()).isZero();
    }

    @Test
    void testNullTopicRejected() {
        assertThatThrownBy(() -> new TopicPartition(null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNegativePartitionRejected() {
        assertThatThrownBy(() -> new TopicPartition("test", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEquality() {
        var tp1 = new TopicPartition("topic", 1);
        var tp2 = new TopicPartition("topic", 1);
        assertThat(tp1).isEqualTo(tp2);
        assertThat(tp1.hashCode()).isEqualTo(tp2.hashCode());
    }

    @Test
    void testInequality() {
        var tp1 = new TopicPartition("topic", 0);
        var tp2 = new TopicPartition("topic", 1);
        assertThat(tp1).isNotEqualTo(tp2);
    }

    @Test
    void testToString() {
        assertThat(new TopicPartition("orders", 3).toString()).isEqualTo("orders-3");
    }

    @Test
    void testAsMapKey() {
        var map = new java.util.HashMap<TopicPartition, String>();
        map.put(new TopicPartition("t", 0), "a");
        map.put(new TopicPartition("t", 1), "b");
        assertThat(map).hasSize(2);
        assertThat(map.get(new TopicPartition("t", 0))).isEqualTo("a");
    }
}
