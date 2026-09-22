package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.transport.InMemoryKafkaTransport;

/**
 * In-memory fixture wiring the headless {@link KafkaBroker} core to client instances over an
 * {@link InMemoryKafkaTransport} pair — no sockets, no ports, deterministic.
 *
 * <p>Test-side equivalent of the production path (service layer +
 * {@code PipelineKafkaTransport}); the transport-injection seam exists so the whole protocol can
 * be exercised without TCP. The server-side end of each pair is fed to
 * {@link KafkaBroker#handleConnection} (the same seam the service uses); the client end is passed
 * to the client constructor. Each client gets its own pair (its own connection).
 */
public final class InMemoryKafka {

    private InMemoryKafka() {}

    /** A running, headless Kafka broker core. Close it when done. */
    public static KafkaBroker broker() {
        var broker = new KafkaBroker("localhost", 0);
        try {
            broker.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return broker;
    }

    /**
     * Feeds one end of a fresh in-memory pair to the broker; returns the pair so the test builds
     * its client on {@code pair[1]} (the server side, {@code pair[0]}, is wired).
     */
    public static InMemoryKafkaTransport[] pair(KafkaBroker broker) {
        var pair = InMemoryKafkaTransport.createPair();
        broker.handleConnection(pair[0]);
        return pair;
    }

    /** A non-idempotent producer connected over a fresh in-memory pair. */
    public static KafkaProducer producer(KafkaBroker broker, String clientId) {
        var pair = pair(broker);
        return new KafkaProducer(pair[1], clientId);
    }

    /** A consumer (defaults) connected over a fresh in-memory pair. */
    public static KafkaConsumer consumer(KafkaBroker broker, String clientId, String groupId) {
        var pair = pair(broker);
        return new KafkaConsumer(pair[1], clientId, groupId);
    }

    /** An admin client connected over a fresh in-memory pair. */
    public static KafkaAdminClient admin(KafkaBroker broker, String clientId) {
        var pair = pair(broker);
        return new KafkaAdminClient(pair[1], clientId);
    }
}
