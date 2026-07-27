package ssg.legoflow.messaging.nats.jetstream;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.messaging.nats.protocol.NatsHeaders;
import ssg.legoflow.messaging.nats.protocol.NatsProtocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * JetStream-aware NATS client for persistent publish and consume.
 *
 * <p>Provides JetStream publish (with acknowledgement) and consume
 * operations via the {@code $JS.API} subjects.
 *
 * @since 1.0.0
 */
public final class JetStreamClient {

    private final NatsClient client;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Creates a JetStream client wrapping a NATS client.
     *
     * @param client the NATS client (must be connected)
     */
    public JetStreamClient(NatsClient client) {
        this.client = Objects.requireNonNull(client);
    }

    /**
     * Publishes a message to a JetStream stream and waits for acknowledgement.
     *
     * @param subject the subject
     * @param data    the payload
     * @return the publish ack response, or null on timeout
     * @throws IOException if publish fails
     */
    public NatsMessage publish(String subject, byte[] data) throws IOException {
        return client.request(subject, data, DEFAULT_TIMEOUT);
    }

    /**
     * Publishes a message with headers to a JetStream stream.
     *
     * @param subject the subject
     * @param headers the headers
     * @param data    the payload
     * @throws IOException if publish fails
     */
    public void publishWithHeaders(String subject, NatsHeaders headers, byte[] data) throws IOException {
        client.publish(subject, headers, data);
    }

    /**
     * Creates a stream via the JetStream API.
     *
     * @param config the stream configuration
     * @return the response message, or null on timeout
     * @throws IOException if request fails
     */
    public NatsMessage createStream(StreamConfig config) throws IOException {
        String subject = NatsProtocol.JS_API_PREFIX + "STREAM.CREATE." + config.name();
        return client.request(subject,
                config.toJson().getBytes(StandardCharsets.UTF_8),
                DEFAULT_TIMEOUT);
    }

    /**
     * Deletes a stream via the JetStream API.
     *
     * @param streamName the stream name
     * @return the response message, or null on timeout
     * @throws IOException if request fails
     */
    public NatsMessage deleteStream(String streamName) throws IOException {
        String subject = NatsProtocol.JS_API_PREFIX + "STREAM.DELETE." + streamName;
        return client.request(subject, new byte[0], DEFAULT_TIMEOUT);
    }

    /**
     * Gets stream info via the JetStream API.
     *
     * @param streamName the stream name
     * @return the response message, or null on timeout
     * @throws IOException if request fails
     */
    public NatsMessage streamInfo(String streamName) throws IOException {
        String subject = NatsProtocol.JS_API_PREFIX + "STREAM.INFO." + streamName;
        return client.request(subject, new byte[0], DEFAULT_TIMEOUT);
    }

    /**
     * Creates a consumer via the JetStream API.
     *
     * @param streamName the stream name
     * @param config     the consumer configuration
     * @return the response message, or null on timeout
     * @throws IOException if request fails
     */
    public NatsMessage createConsumer(String streamName, ConsumerConfig config) throws IOException {
        String subject = NatsProtocol.JS_API_PREFIX + "CONSUMER.CREATE." + streamName;
        return client.request(subject,
                config.toJson().getBytes(StandardCharsets.UTF_8),
                DEFAULT_TIMEOUT);
    }

    /**
     * Deletes a consumer via the JetStream API.
     *
     * @param streamName   the stream name
     * @param consumerName the consumer name
     * @return the response message, or null on timeout
     * @throws IOException if request fails
     */
    public NatsMessage deleteConsumer(String streamName, String consumerName) throws IOException {
        String subject = NatsProtocol.JS_API_PREFIX + "CONSUMER.DELETE." + streamName + "." + consumerName;
        return client.request(subject, new byte[0], DEFAULT_TIMEOUT);
    }

    /**
     * Returns the underlying NATS client.
     *
     * @return the client
     */
    public NatsClient natsClient() {
        return client;
    }
}
