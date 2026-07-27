package ssg.legoflow.messaging.nats.jetstream;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ConsumerConfig}.
 */
class ConsumerConfigTest {

    @Test
    void testBuilderDefaults() {
        var config = ConsumerConfig.builder().build();
        assertThat(config.durableName()).isNull();
        assertThat(config.deliverPolicy()).isEqualTo(ConsumerConfig.DeliverPolicy.ALL);
        assertThat(config.ackPolicy()).isEqualTo(AckPolicy.EXPLICIT);
        assertThat(config.ackWait()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.maxDeliver()).isEqualTo(-1);
        assertThat(config.replayPolicy()).isEqualTo(ConsumerConfig.ReplayPolicy.INSTANT);
        assertThat(config.filterSubject()).isNull();
        assertThat(config.maxAckPending()).isEqualTo(1000);
    }

    @Test
    void testBuilderAllFields() {
        var config = ConsumerConfig.builder()
                .durable("my-consumer")
                .deliverPolicy(ConsumerConfig.DeliverPolicy.NEW)
                .ackPolicy(AckPolicy.NONE)
                .ackWait(Duration.ofSeconds(60))
                .maxDeliver(5)
                .replayPolicy(ConsumerConfig.ReplayPolicy.ORIGINAL)
                .filterSubject("orders.new")
                .maxAckPending(500)
                .startSeq(100)
                .build();

        assertThat(config.durableName()).isEqualTo("my-consumer");
        assertThat(config.deliverPolicy()).isEqualTo(ConsumerConfig.DeliverPolicy.NEW);
        assertThat(config.ackPolicy()).isEqualTo(AckPolicy.NONE);
        assertThat(config.maxDeliver()).isEqualTo(5);
        assertThat(config.replayPolicy()).isEqualTo(ConsumerConfig.ReplayPolicy.ORIGINAL);
        assertThat(config.filterSubject()).isEqualTo("orders.new");
        assertThat(config.maxAckPending()).isEqualTo(500);
        assertThat(config.startSeq()).isEqualTo(100);
    }

    @Test
    void testIsDurable() {
        assertThat(ConsumerConfig.builder().durable("d").build().isDurable()).isTrue();
        assertThat(ConsumerConfig.builder().build().isDurable()).isFalse();
    }

    @Test
    void testToJsonAndFromJson() {
        var original = ConsumerConfig.builder()
                .durable("proc")
                .deliverPolicy(ConsumerConfig.DeliverPolicy.LAST)
                .ackPolicy(AckPolicy.ALL)
                .build();
        var parsed = ConsumerConfig.fromJson(original.toJson());

        assertThat(parsed.durableName()).isEqualTo("proc");
        assertThat(parsed.deliverPolicy()).isEqualTo(ConsumerConfig.DeliverPolicy.LAST);
        assertThat(parsed.ackPolicy()).isEqualTo(AckPolicy.ALL);
    }

    @Test
    void testDeliverPolicyFromValue() {
        assertThat(ConsumerConfig.DeliverPolicy.fromValue("all")).isEqualTo(ConsumerConfig.DeliverPolicy.ALL);
        assertThat(ConsumerConfig.DeliverPolicy.fromValue("last")).isEqualTo(ConsumerConfig.DeliverPolicy.LAST);
        assertThat(ConsumerConfig.DeliverPolicy.fromValue("new")).isEqualTo(ConsumerConfig.DeliverPolicy.NEW);
        assertThat(ConsumerConfig.DeliverPolicy.fromValue("by_start_sequence")).isEqualTo(ConsumerConfig.DeliverPolicy.BY_START_SEQ);
        assertThat(ConsumerConfig.DeliverPolicy.fromValue("unknown")).isEqualTo(ConsumerConfig.DeliverPolicy.ALL);
    }

    @Test
    void testReplayPolicyFromValue() {
        assertThat(ConsumerConfig.ReplayPolicy.fromValue("instant")).isEqualTo(ConsumerConfig.ReplayPolicy.INSTANT);
        assertThat(ConsumerConfig.ReplayPolicy.fromValue("original")).isEqualTo(ConsumerConfig.ReplayPolicy.ORIGINAL);
    }
}
