package ssg.legoflow.messaging.kafka.service;

import ssg.legoflow.messaging.kafka.client.KafkaConsumer;
import ssg.legoflow.messaging.kafka.client.KafkaProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test: Kafka client services ↔ broker service over real TCP through
 * the {@link SelectableChannelManager} (mirrors the NATS {@code NatsServiceIntegrationTest}).
 *
 * <p>Exercises the full production pipeline: SocketChannel → SelectableChannelManager →
 * TcpDataChannel → ChannelPipeline → {@link ssg.legoflow.messaging.kafka.transport.PipelineKafkaTransport}
 * → Kafka protocol core. The protocol core itself never touches sockets — this test only
 * drives the service layer, which owns the (manager-managed) channels.
 *
 * <p>The multi-message produce is the transport/codec canary the skill calls for: in-memory
 * pairs hide coalesced TCP segments and read timeouts, so 5+ small messages against the real
 * broker is what catches those contract bugs.
 */
class KafkaServiceIntegrationTest {

    private SelectableChannelManager manager;
    private DefaultServiceContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new SelectableChannelManager(ctx);
        ctx.setAttribute("channelManager", manager);
        manager.startEventLoop();
    }

    @AfterEach
    void tearDown() {
        manager.stopEventLoop();
        manager.close();
    }

    @Test
    void testBrokerServiceLifecycleOnly() throws Exception {
        var server = KafkaBrokerService.builder().port(0).build();
        server.connect(ctx);
        int port = server.getPort();
        assertThat(port).as("broker service should bind a real port").isGreaterThan(0);
        assertThat(server.getBroker()).as("headless broker core should be created and started").isNotNull();
        // Disconnect must stop the broker core and release the bound port without throwing.
        assertThatCode(() -> server.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test
    void testProduceMultiMessageOverTcp() throws Exception {
        var server = KafkaBrokerService.builder().port(0).build();
        server.connect(ctx);
        int port = server.getPort();
        assertThat(port).isGreaterThan(0);
        server.getBroker().createTopic("tcp-test", 3);

        var client = KafkaClientService.builder("localhost", port).build();
        client.connect(ctx);
        var transport = client.getTransport();
        assertThat(transport).as("client transport should be open after connect").isNotNull();
        assertThat(transport.isOpen()).isTrue();

        try (var producer = new KafkaProducer(transport, "tcp-producer")) {
            producer.init();
            // 5 small messages over real TCP — the coalesced-segment / reassembly canary.
            for (int i = 0; i < 5; i++) {
                var result = producer.send("tcp-test", "key-" + i, "value-" + i);
                assertThat(result.topic()).isEqualTo("tcp-test");
                assertThat(result.partition()).isBetween(0, 2);
                assertThat(result.offset()).as("offset should be >= 0 for msg " + i).isGreaterThanOrEqualTo(0);
            }
        }

        client.disconnect(ctx);
        server.disconnect(ctx);
    }

    @Test
    void testConsumeOverTcp() throws Exception {
        var server = KafkaBrokerService.builder().port(0).build();
        server.connect(ctx);
        int port = server.getPort();
        server.getBroker().createTopic("tcp-test", 3);

        // Produce over one connection...
        var producerClient = KafkaClientService.builder("localhost", port).build();
        producerClient.connect(ctx);
        try (var producer = new KafkaProducer(producerClient.getTransport(), "tcp-producer")) {
            producer.init();
            for (int i = 0; i < 5; i++) {
                producer.send("tcp-test", "key-" + i, "value-" + i);
            }
        }
        producerClient.disconnect(ctx);

        // ... and consume over a separate connection.
        var consumerClient = KafkaClientService.builder("localhost", port).build();
        consumerClient.connect(ctx);
        try (var consumer = new KafkaConsumer(consumerClient.getTransport(), "tcp-consumer", "tcp-group")) {
            consumer.subscribe(List.of("tcp-test"));
            var records = consumer.poll(5000);
            assertThat(records).as("consumer should fetch the produced messages over TCP")
                    .hasSizeGreaterThanOrEqualTo(5);
        }
        consumerClient.disconnect(ctx);
        server.disconnect(ctx);
    }
}
