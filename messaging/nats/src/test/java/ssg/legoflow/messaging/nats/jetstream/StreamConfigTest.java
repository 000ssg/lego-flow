package ssg.legoflow.messaging.nats.jetstream;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StreamConfig}.
 */
class StreamConfigTest {

    @Test
    void testBuilderDefaults() {
        var config = StreamConfig.builder("TEST").subjects("test.>").build();
        assertThat(config.name()).isEqualTo("TEST");
        assertThat(config.subjects()).containsExactly("test.>");
        assertThat(config.retention()).isEqualTo(StreamConfig.RetentionPolicy.LIMITS);
        assertThat(config.maxConsumers()).isEqualTo(-1);
        assertThat(config.maxMsgs()).isEqualTo(-1);
        assertThat(config.maxBytes()).isEqualTo(-1);
        assertThat(config.storage()).isEqualTo(StreamConfig.StorageType.MEMORY);
        assertThat(config.numReplicas()).isEqualTo(1);
        assertThat(config.discardPolicy()).isEqualTo(StreamConfig.DiscardPolicy.OLD);
    }

    @Test
    void testBuilderAllFields() {
        var config = StreamConfig.builder("ORDERS")
                .subjects("orders.>", "returns.>")
                .retention(StreamConfig.RetentionPolicy.WORKQUEUE)
                .maxConsumers(5)
                .maxMsgs(10000)
                .maxBytes(1024 * 1024)
                .maxAge(Duration.ofHours(24))
                .storage(StreamConfig.StorageType.MEMORY)
                .numReplicas(3)
                .discardPolicy(StreamConfig.DiscardPolicy.NEW)
                .duplicateWindow(Duration.ofMinutes(5))
                .build();

        assertThat(config.subjects()).containsExactly("orders.>", "returns.>");
        assertThat(config.retention()).isEqualTo(StreamConfig.RetentionPolicy.WORKQUEUE);
        assertThat(config.maxConsumers()).isEqualTo(5);
        assertThat(config.maxMsgs()).isEqualTo(10000);
        assertThat(config.maxBytes()).isEqualTo(1024 * 1024);
        assertThat(config.maxAge()).isEqualTo(Duration.ofHours(24));
        assertThat(config.numReplicas()).isEqualTo(3);
        assertThat(config.discardPolicy()).isEqualTo(StreamConfig.DiscardPolicy.NEW);
    }

    @Test
    void testNullNameThrows() {
        assertThatThrownBy(() -> StreamConfig.builder(null).subjects("s").build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testEmptyNameThrows() {
        assertThatThrownBy(() -> StreamConfig.builder("").subjects("s").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEmptySubjectsThrows() {
        assertThatThrownBy(() -> new StreamConfig("T", List.of(), null,
                -1, -1, -1, null, null, 1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSubjectsImmutable() {
        var config = StreamConfig.builder("T").subjects("a", "b").build();
        assertThatThrownBy(() -> config.subjects().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testToJsonContainsFields() {
        var config = StreamConfig.builder("EVENTS")
                .subjects("events.>")
                .retention(StreamConfig.RetentionPolicy.INTEREST)
                .build();
        String json = config.toJson();

        assertThat(json).contains("\"name\":\"EVENTS\"");
        assertThat(json).contains("\"subjects\":[\"events.>\"]");
        assertThat(json).contains("\"retention\":\"interest\"");
    }

    @Test
    void testRoundTrip() {
        var original = StreamConfig.builder("MY_STREAM")
                .subjects("a.>", "b.>")
                .retention(StreamConfig.RetentionPolicy.WORKQUEUE)
                .maxMsgs(5000)
                .build();
        var parsed = StreamConfig.fromJson(original.toJson());

        assertThat(parsed.name()).isEqualTo("MY_STREAM");
        assertThat(parsed.subjects()).containsExactly("a.>", "b.>");
        assertThat(parsed.retention()).isEqualTo(StreamConfig.RetentionPolicy.WORKQUEUE);
        assertThat(parsed.maxMsgs()).isEqualTo(5000);
    }

    @Test
    void testRetentionPolicyFromValue() {
        assertThat(StreamConfig.RetentionPolicy.fromValue("limits")).isEqualTo(StreamConfig.RetentionPolicy.LIMITS);
        assertThat(StreamConfig.RetentionPolicy.fromValue("interest")).isEqualTo(StreamConfig.RetentionPolicy.INTEREST);
        assertThat(StreamConfig.RetentionPolicy.fromValue("workqueue")).isEqualTo(StreamConfig.RetentionPolicy.WORKQUEUE);
        assertThat(StreamConfig.RetentionPolicy.fromValue("unknown")).isEqualTo(StreamConfig.RetentionPolicy.LIMITS);
    }

    @Test
    void testStorageTypeFromValue() {
        assertThat(StreamConfig.StorageType.fromValue("memory")).isEqualTo(StreamConfig.StorageType.MEMORY);
        assertThat(StreamConfig.StorageType.fromValue("file")).isEqualTo(StreamConfig.StorageType.FILE);
    }

    @Test
    void testDiscardPolicyFromValue() {
        assertThat(StreamConfig.DiscardPolicy.fromValue("old")).isEqualTo(StreamConfig.DiscardPolicy.OLD);
        assertThat(StreamConfig.DiscardPolicy.fromValue("new")).isEqualTo(StreamConfig.DiscardPolicy.NEW);
    }
}
