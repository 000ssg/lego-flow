package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.codec.KafkaCodec;
import ssg.legoflow.messaging.kafka.common.*;
import ssg.legoflow.messaging.kafka.protocol.*;
import ssg.legoflow.messaging.kafka.record.Compression;
import ssg.legoflow.messaging.kafka.record.Record;
import ssg.legoflow.messaging.kafka.record.RecordBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka producer client.
 *
 * <p>Features:
 * <ul>
 *   <li>Configurable partitioner (key hash, round-robin)</li>
 *   <li>Record batching</li>
 *   <li>Compression (gzip)</li>
 *   <li>Configurable acks (0/1/all)</li>
 *   <li>Retries with backoff</li>
 *   <li>Idempotent mode</li>
 *   <li>Transactional mode</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class KafkaProducer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducer.class);

    private final KafkaConnection connection;
    private final Partitioner partitioner;
    private final short acks;
    private final int retries;
    private final long retryBackoffMs;
    private final Compression compression;
    private final int timeoutMs;
    private final boolean idempotent;
    private final String transactionalId;

    // Metadata cache
    private final Map<String, Integer> topicPartitionCounts = new ConcurrentHashMap<>();

    // Idempotent producer state
    private volatile long producerId = -1;
    private volatile short producerEpoch = -1;
    private final Map<TopicPartition, AtomicInteger> sequenceNumbers = new ConcurrentHashMap<>();

    // Transaction state
    private volatile boolean inTransaction = false;

    /**
     * Creates a new Kafka producer.
     *
     * @param host            the broker host
     * @param port            the broker port
     * @param clientId        the client ID
     * @param partitioner     the partitioner strategy
     * @param acks            the acknowledgment mode (-1, 0, 1)
     * @param retries         the number of retries
     * @param retryBackoffMs  the retry backoff in milliseconds
     * @param compression     the compression type
     * @param idempotent      whether to enable idempotent mode
     * @param transactionalId the transactional ID (null for non-transactional)
     */
    public KafkaProducer(String host, int port, String clientId, Partitioner partitioner,
                         short acks, int retries, long retryBackoffMs, Compression compression,
                         boolean idempotent, String transactionalId) {
        this.connection = new KafkaConnection(host, port, clientId);
        this.partitioner = partitioner != null ? partitioner : Partitioner.keyHash();
        this.acks = acks;
        this.retries = retries;
        this.retryBackoffMs = retryBackoffMs;
        this.compression = compression;
        this.timeoutMs = 30000;
        this.idempotent = idempotent || transactionalId != null;
        this.transactionalId = transactionalId;
    }

    /**
     * Creates a simple non-idempotent producer.
     *
     * @param host     the broker host
     * @param port     the broker port
     * @param clientId the client ID
     */
    public KafkaProducer(String host, int port, String clientId) {
        this(host, port, clientId, null, (short) 1, 3, 100, Compression.NONE, false, null);
    }

    /**
     * Initializes the producer, establishing connection and (if idempotent) obtaining a producer ID.
     *
     * @throws IOException if connection or initialization fails
     */
    public void init() throws IOException {
        connection.connect();

        if (idempotent) {
            initProducerId();
        }
    }

    private void initProducerId() throws IOException {
        byte[] payload = KafkaCodec.encodeInitProducerIdRequest(
                new InitProducerIdRequest(transactionalId, 60000));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.INIT_PRODUCER_ID.key(), (short) 0, payload);
        InitProducerIdResponse response = KafkaCodec.decodeInitProducerIdResponse(resp);

        if (response.errorCode() != KafkaErrors.NONE.code()) {
            throw new IOException("InitProducerId failed: " + KafkaErrors.forCode(response.errorCode()));
        }
        this.producerId = response.producerId();
        this.producerEpoch = response.producerEpoch();
        LOG.info("Initialized producer: id={}, epoch={}", producerId, producerEpoch);
    }

    /**
     * Sends a record to a topic.
     *
     * @param topic the topic name
     * @param key   the record key (may be null)
     * @param value the record value
     * @return the produce result
     * @throws IOException if the send fails
     */
    public ProduceResult send(String topic, byte[] key, byte[] value) throws IOException {
        return send(topic, key, value, List.of());
    }

    /**
     * Sends a record to a topic with string key and value.
     *
     * @param topic the topic name
     * @param key   the string key (may be null)
     * @param value the string value
     * @return the produce result
     * @throws IOException if the send fails
     */
    public ProduceResult send(String topic, String key, String value) throws IOException {
        return send(topic,
                key != null ? key.getBytes(StandardCharsets.UTF_8) : null,
                value != null ? value.getBytes(StandardCharsets.UTF_8) : null);
    }

    /**
     * Sends a record to a topic with headers.
     *
     * @param topic   the topic name
     * @param key     the record key (may be null)
     * @param value   the record value
     * @param headers the record headers
     * @return the produce result
     * @throws IOException if the send fails
     */
    public ProduceResult send(String topic, byte[] key, byte[] value,
                              List<ssg.legoflow.messaging.kafka.record.Header> headers) throws IOException {
        // Determine partition
        int numPartitions = getPartitionCount(topic);
        int partition = partitioner.partition(topic, key, value, numPartitions);

        return sendToPartition(topic, partition, key, value, headers);
    }

    /**
     * Sends a record to a specific partition.
     *
     * @param topic     the topic name
     * @param partition the partition index
     * @param key       the record key (may be null)
     * @param value     the record value
     * @param headers   the record headers
     * @return the produce result
     * @throws IOException if the send fails
     */
    public ProduceResult sendToPartition(String topic, int partition, byte[] key, byte[] value,
                                         List<ssg.legoflow.messaging.kafka.record.Header> headers)
            throws IOException {

        // Build record batch
        long now = System.currentTimeMillis();
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .baseTimestamp(now)
                .maxTimestamp(now)
                .compression(compression)
                .producerId(producerId)
                .producerEpoch(producerEpoch);

        if (idempotent) {
            TopicPartition tp = new TopicPartition(topic, partition);
            int seq = sequenceNumbers.computeIfAbsent(tp, k -> new AtomicInteger(0)).getAndIncrement();
            batch.baseSequence(seq);
        }

        Record record = new Record(0, 0L, key, value, headers != null ? headers : List.of());
        batch.records(List.of(record));
        batch.lastOffsetDelta(0);

        byte[] batchBytes = batch.encode();

        // Build produce request
        ProduceRequest request = new ProduceRequest(
                inTransaction ? transactionalId : null, acks, timeoutMs,
                List.of(new ProduceRequest.TopicData(topic,
                        List.of(new ProduceRequest.PartitionData(partition, batchBytes)))));

        byte[] payload = KafkaCodec.encodeProduceRequest(request);

        // Send with retries
        IOException lastError = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                ByteBuffer resp = connection.sendAndReceive(ApiKey.PRODUCE.key(), (short) 0, payload);
                ProduceResponse response = KafkaCodec.decodeProduceResponse(resp);

                for (var tr : response.responses()) {
                    for (var pr : tr.partitionResponses()) {
                        if (pr.errorCode() != KafkaErrors.NONE.code()) {
                            KafkaErrors error = KafkaErrors.forCode(pr.errorCode());
                            if (error == KafkaErrors.DUPLICATE_SEQUENCE_NUMBER) {
                                // Duplicate — return success (idempotent guarantee)
                                return new ProduceResult(topic, partition, pr.baseOffset());
                            }
                            throw new IOException("Produce failed: " + error.message());
                        }
                        return new ProduceResult(topic, partition, pr.baseOffset());
                    }
                }
                throw new IOException("Empty produce response");
            } catch (IOException e) {
                lastError = e;
                if (attempt < retries) {
                    try {
                        Thread.sleep(retryBackoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw lastError;
    }

    /**
     * Begins a transaction.
     *
     * @throws IllegalStateException if no transactional ID was configured
     */
    public void beginTransaction() {
        if (transactionalId == null) {
            throw new IllegalStateException("Cannot begin transaction without transactional ID");
        }
        inTransaction = true;
        LOG.debug("Transaction begun: {}", transactionalId);
    }

    /**
     * Adds partitions to the current transaction.
     *
     * @param partitions the partitions to add
     * @throws IOException if the request fails
     */
    public void addPartitionsToTransaction(List<TopicPartition> partitions) throws IOException {
        if (!inTransaction) throw new IllegalStateException("No active transaction");

        // Group by topic
        Map<String, List<Integer>> byTopic = new LinkedHashMap<>();
        for (TopicPartition tp : partitions) {
            byTopic.computeIfAbsent(tp.topic(), k -> new ArrayList<>()).add(tp.partition());
        }
        List<AddPartitionsToTxnRequest.TopicPartitions> topics = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            topics.add(new AddPartitionsToTxnRequest.TopicPartitions(entry.getKey(), entry.getValue()));
        }

        byte[] payload = KafkaCodec.encodeAddPartitionsToTxnRequest(
                new AddPartitionsToTxnRequest(transactionalId, producerId, producerEpoch, topics));
        ByteBuffer resp = connection.sendAndReceive(
                ApiKey.ADD_PARTITIONS_TO_TXN.key(), (short) 0, payload);
        AddPartitionsToTxnResponse response = KafkaCodec.decodeAddPartitionsToTxnResponse(resp);

        for (var t : response.topics()) {
            for (var p : t.partitions()) {
                if (p.errorCode() != KafkaErrors.NONE.code()) {
                    throw new IOException("AddPartitionsToTxn failed: "
                            + KafkaErrors.forCode(p.errorCode()).message());
                }
            }
        }
    }

    /**
     * Commits the current transaction.
     *
     * @throws IOException if the commit fails
     */
    public void commitTransaction() throws IOException {
        endTransaction(true);
    }

    /**
     * Aborts the current transaction.
     *
     * @throws IOException if the abort fails
     */
    public void abortTransaction() throws IOException {
        endTransaction(false);
    }

    private void endTransaction(boolean commit) throws IOException {
        if (!inTransaction) throw new IllegalStateException("No active transaction");

        byte[] payload = KafkaCodec.encodeEndTxnRequest(
                new EndTxnRequest(transactionalId, producerId, producerEpoch, commit));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.END_TXN.key(), (short) 0, payload);
        EndTxnResponse response = KafkaCodec.decodeEndTxnResponse(resp);

        if (response.errorCode() != KafkaErrors.NONE.code()) {
            throw new IOException("EndTxn failed: " + KafkaErrors.forCode(response.errorCode()).message());
        }

        inTransaction = false;
        LOG.debug("Transaction {}: {}", commit ? "committed" : "aborted", transactionalId);
    }

    /**
     * Refreshes metadata for a topic.
     *
     * @param topic the topic name
     * @throws IOException if the request fails
     */
    public void refreshMetadata(String topic) throws IOException {
        byte[] payload = KafkaCodec.encodeMetadataRequest(new MetadataRequest(List.of(topic)));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.METADATA.key(), (short) 0, payload);
        MetadataResponse response = KafkaCodec.decodeMetadataResponse(resp);

        for (var t : response.topics()) {
            if (t.errorCode() == KafkaErrors.NONE.code()) {
                topicPartitionCounts.put(t.name(), t.partitions().size());
            }
        }
    }

    private int getPartitionCount(String topic) throws IOException {
        Integer count = topicPartitionCounts.get(topic);
        if (count == null) {
            refreshMetadata(topic);
            count = topicPartitionCounts.get(topic);
        }
        return count != null && count > 0 ? count : 1;
    }

    /**
     * Result of a produce operation.
     *
     * @param topic      the topic
     * @param partition  the partition
     * @param offset     the assigned offset
     */
    public record ProduceResult(String topic, int partition, long offset) {
    }

    @Override
    public void close() {
        connection.close();
    }
}
