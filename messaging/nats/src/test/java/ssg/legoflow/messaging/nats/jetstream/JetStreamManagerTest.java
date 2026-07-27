package ssg.legoflow.messaging.nats.jetstream;

import ssg.legoflow.messaging.nats.server.NatsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link JetStreamManager}.
 */
class JetStreamManagerTest {

    private NatsServer server;
    private JetStreamManager jsm;

    @BeforeEach
    void setUp() throws IOException {
        server = new NatsServer();
        server.start(0);
        jsm = server.jetStreamManager();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    // --- Stream operations ---

    @Test
    void testCreateStream() {
        var config = StreamConfig.builder("ORDERS").subjects("orders.>").build();
        var stream = jsm.createStream(config);

        assertThat(stream.name()).isEqualTo("ORDERS");
        assertThat(stream.config().subjects()).containsExactly("orders.>");
    }

    @Test
    void testCreateDuplicateStreamThrows() {
        var config = StreamConfig.builder("TEST").subjects("test.>").build();
        jsm.createStream(config);

        assertThatThrownBy(() -> jsm.createStream(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void testDeleteStream() {
        jsm.createStream(StreamConfig.builder("TEST").subjects("test.>").build());
        assertThat(jsm.deleteStream("TEST")).isTrue();
        assertThat(jsm.getStream("TEST")).isNull();
    }

    @Test
    void testDeleteNonExistentStream() {
        assertThat(jsm.deleteStream("MISSING")).isFalse();
    }

    @Test
    void testGetStream() {
        jsm.createStream(StreamConfig.builder("S1").subjects("s1.>").build());
        assertThat(jsm.getStream("S1")).isNotNull();
        assertThat(jsm.getStream("MISSING")).isNull();
    }

    @Test
    void testStreamNames() {
        jsm.createStream(StreamConfig.builder("A").subjects("a").build());
        jsm.createStream(StreamConfig.builder("B").subjects("b").build());

        assertThat(jsm.streamNames()).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void testStreams() {
        jsm.createStream(StreamConfig.builder("X").subjects("x").build());
        assertThat(jsm.streams()).hasSize(1);
    }

    @Test
    void testPurgeStream() {
        jsm.createStream(StreamConfig.builder("P").subjects("p.>").build());
        var stream = jsm.getStream("P");
        stream.store().store("p.a", null, "data".getBytes());
        stream.store().store("p.b", null, "more".getBytes());

        int purged = jsm.purgeStream("P");
        assertThat(purged).isEqualTo(2);
        assertThat(stream.store().messageCount()).isEqualTo(0);
    }

    @Test
    void testPurgeNonExistentStream() {
        assertThat(jsm.purgeStream("MISSING")).isEqualTo(-1);
    }

    // --- Consumer operations ---

    @Test
    void testCreateDurableConsumer() {
        jsm.createStream(StreamConfig.builder("S").subjects("s.>").build());
        var config = ConsumerConfig.builder().durable("consumer1").build();
        var consumer = jsm.createConsumer("S", config);

        assertThat(consumer.name()).isEqualTo("consumer1");
        assertThat(consumer.config().isDurable()).isTrue();
    }

    @Test
    void testCreateEphemeralConsumer() {
        jsm.createStream(StreamConfig.builder("S").subjects("s.>").build());
        var config = ConsumerConfig.builder().build();
        var consumer = jsm.createConsumer("S", config);

        assertThat(consumer.name()).startsWith("ephemeral-");
    }

    @Test
    void testCreateConsumerOnMissingStream() {
        assertThatThrownBy(() -> jsm.createConsumer("MISSING", ConsumerConfig.builder().build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDeleteConsumer() {
        jsm.createStream(StreamConfig.builder("S").subjects("s.>").build());
        jsm.createConsumer("S", ConsumerConfig.builder().durable("c1").build());

        assertThat(jsm.deleteConsumer("S", "c1")).isTrue();
        assertThat(jsm.getConsumer("S", "c1")).isNull();
    }

    @Test
    void testDeleteConsumerNotFound() {
        jsm.createStream(StreamConfig.builder("S").subjects("s.>").build());
        assertThat(jsm.deleteConsumer("S", "missing")).isFalse();
    }

    @Test
    void testGetConsumer() {
        jsm.createStream(StreamConfig.builder("S").subjects("s.>").build());
        jsm.createConsumer("S", ConsumerConfig.builder().durable("c1").build());

        assertThat(jsm.getConsumer("S", "c1")).isNotNull();
        assertThat(jsm.getConsumer("S", "missing")).isNull();
        assertThat(jsm.getConsumer("MISSING", "c1")).isNull();
    }

    // --- PullSubscription ---

    @Test
    void testPullSubscribe() {
        jsm.createStream(StreamConfig.builder("S").subjects("s.>").build());
        jsm.createConsumer("S", ConsumerConfig.builder().durable("c1").build());

        var pull = jsm.pullSubscribe("S", "c1");
        assertThat(pull).isNotNull();
        assertThat(pull.stream().name()).isEqualTo("S");
        assertThat(pull.consumer().name()).isEqualTo("c1");
    }

    @Test
    void testPullSubscribeMissingStream() {
        assertThatThrownBy(() -> jsm.pullSubscribe("MISSING", "c1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPullSubscribeMissingConsumer() {
        jsm.createStream(StreamConfig.builder("S").subjects("s.>").build());
        assertThatThrownBy(() -> jsm.pullSubscribe("S", "missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Message routing ---

    @Test
    void testHandlePublishStoresInStream() {
        jsm.createStream(StreamConfig.builder("EVENTS").subjects("events.>").build());

        jsm.handlePublish("events.login", null, "user=alice".getBytes(), null);
        jsm.handlePublish("events.logout", null, "user=bob".getBytes(), null);

        var stream = jsm.getStream("EVENTS");
        assertThat(stream.store().messageCount()).isEqualTo(2);
    }

    @Test
    void testHandlePublishIgnoresJetStreamApi() {
        jsm.createStream(StreamConfig.builder("ALL").subjects(">").build());

        jsm.handlePublish("$JS.API.STREAM.CREATE.TEST", null, "{}".getBytes(), null);

        var stream = jsm.getStream("ALL");
        assertThat(stream.store().messageCount()).isEqualTo(0);
    }

    @Test
    void testHandlePublishNoMatchingStream() {
        jsm.createStream(StreamConfig.builder("ORDERS").subjects("orders.>").build());

        // This should not throw, just not store
        jsm.handlePublish("events.login", null, "data".getBytes(), null);

        var stream = jsm.getStream("ORDERS");
        assertThat(stream.store().messageCount()).isEqualTo(0);
    }

    @Test
    void testHandlePublishMultipleStreams() {
        jsm.createStream(StreamConfig.builder("A").subjects("events.>").build());
        jsm.createStream(StreamConfig.builder("B").subjects("events.login").build());

        jsm.handlePublish("events.login", null, "data".getBytes(), null);

        assertThat(jsm.getStream("A").store().messageCount()).isEqualTo(1);
        assertThat(jsm.getStream("B").store().messageCount()).isEqualTo(1);
    }
}
