package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.AddPartitionsToTxnRequest;
import ssg.legoflow.messaging.kafka.protocol.AddPartitionsToTxnResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka AddPartitionsToTxn request (API key 24) and response — register
 * partitions with a transaction so the broker can produce them with the transactional
 * producer.
 * <p>
 * Client/broker interaction is versioned from v0 (the baseline that
 * {@code KafkaAdminClient} sends), while the full version range v0–v4 is encoded so a frame
 * carrying any AddPartitionsToTxn version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/AddPartitionsToTxnRequest.json} and
 * {@code AddPartitionsToTxnResponse.json}):
 * <ul>
 *   <li>v0–v2 (both): baseline, byte-identical. Request: {@code V3AndBelowTransactionalId}
 *       (int16 string) + {@code ProducerId} (int64) + {@code ProducerEpoch} (int16) +
 *       {@code Topics[] { Name, Partitions[] int32 }}. Response: leading {@code
 *       ThrottleTimeMs} (int32, present from v0) + {@code ResultsByTopicV3AndBelow[] { Name,
 *       ResultsByPartition[] { PartitionIndex, PartitionErrorCode } }}.</li>
 *   <li>v3 (both): flexible encoding (KIP-482): compact strings, varint N+1 counts, tagged
 *       fields on every struct level (topic, partition, transaction, top). No tagged fields
 *       are emitted (all zero); received tagged fields are skipped.</li>
 *   <li>v4 (both): reorganized into a batch. Request: {@code Transactions[] {
 *       TransactionalId, ProducerId, ProducerEpoch, VerifyOnly (bool, spec default false),
 *       Topics[] }}. Response: leading {@code ThrottleTimeMs} + ignorable {@code ErrorCode}
 *       + {@code ResultsByTransaction[] { TransactionalId, TopicResults[] }}. The in-house
 *       model is a single transaction — v4 encodes it as a one-element batch; a v4 response
 *       with a single transaction decodes to the model shape.</li>
 * </ul>
 * The model does not expose {@code ThrottleTimeMs} (response) or {@code VerifyOnly}
 * (request v4): the spec defaults ({@code 0}, {@code false}) are written and read +
 * discarded.
 *
 * @since 0.1.0
 */
public final class AddPartitionsToTxnCodec {

    /** Pinned version for client/broker AddPartitionsToTxn interaction (v0). */
    public static final short MIN_VERSION = 0;

    /** Flexible-encoding versions (v3+): compact strings/arrays + tagged fields. */
    public static final short FLEXIBLE_START = 3;

    /** Highest API version with a defined wire format in the spec (v4). */
    public static final short MAX_VERSION = 4;

    /** v4 reorganizes both directions into a batch keyed by transactional ID. */
    private static final short V4_BATCH_START = 4;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    private AddPartitionsToTxnCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "AddPartitionsToTxn v" + version + " not implemented (range 0–4)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────
    /**
     * Encode an AddPartitionsToTxn request body at the given API version.
     *
     * @param version the API version (0–4)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, AddPartitionsToTxnRequest req) {
        checkVersion(version);
        if (version >= V4_BATCH_START) {
            return batchRequest(version, req);
        }
        return singleRequest(version, req);
    }

    /**
     * Decode an AddPartitionsToTxn request body at the given API version.
     *
     * @param version the API version (0–4)
     * @param buf     the request body
     * @return the decoded request
     */
    public static AddPartitionsToTxnRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version >= V4_BATCH_START) {
            return batchRequest(version, buf);
        }
        return singleRequest(version, buf);
    }

    // ── v0–v3 request (v0/v1/v2 byte-identical; v3 flexible) ─────────────
    // Order: V3AndBelowTransactionalId(string), V3AndBelowProducerId(int64),
    //   V3AndBelowProducerEpoch(int16), V3AndBelowTopics[] { Name(string),
    //   Partitions[] int32 }

    private static byte[] singleRequest(short version, AddPartitionsToTxnRequest req) {
        boolean flexible = version >= FLEXIBLE_START;
        int size = 4 + 4; // V3AndBelowTransactionalId (max int16 + int16 len) + V3AndBelowProducerId
        size += 2; // V3AndBelowProducerEpoch
        size += 4; // numTopics (fixed-width only)
        for (AddPartitionsToTxnRequest.TopicPartitions t : req.topics()) {
            size += 4 + 2 + t.name().getBytes(StandardCharsets.UTF_8).length; // name (fixed: int16 len)
            size += 4; // numPartitions (fixed-width only)
            size += t.partitionIndexes().size() * 4; // Partitions int32
            if (flexible) {
                size += 1; // per-topic endTags
                size += 1; // per-partition endTags (1 per partition)
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (flexible) {
            KafkaCodecPrimitives.writeCompactString(buf, req.transactionalId()); // nullable
        } else {
            KafkaCodecPrimitives.writeNullableString(buf, req.transactionalId()); // nullable
        }
        buf.putLong(req.producerId());
        buf.putShort(req.producerEpoch());
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1); // KIP-482: N+1
        } else {
            buf.putInt(req.topics().size());
        }
        for (AddPartitionsToTxnRequest.TopicPartitions t : req.topics()) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, t.name());
            } else {
                KafkaCodecPrimitives.writeString(buf, t.name());
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, t.partitionIndexes().size() + 1); // KIP-482: N+1
            } else {
                buf.putInt(t.partitionIndexes().size());
            }
            for (int idx : t.partitionIndexes()) {
                buf.putInt(idx);
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AddPartitionsToTxnRequest singleRequest(short version, ByteBuffer b) {
        boolean flexible = version >= FLEXIBLE_START;
        String txnId = flexible
                ? KafkaCodecPrimitives.readCompactString(b)
                : KafkaCodecPrimitives.readNullableString(b);
        long producerId = b.getLong();
        short epoch = b.getShort();
        int topicCount = flexible
                ? KafkaCodecPrimitives.readVarint(b) - 1 // KIP-482: N+1
                : b.getInt();
        List<AddPartitionsToTxnRequest.TopicPartitions> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = flexible
                    ? KafkaCodecPrimitives.readCompactString(b)
                    : KafkaCodecPrimitives.readString(b);
            int partCount = flexible
                    ? KafkaCodecPrimitives.readVarint(b) - 1 // KIP-482: N+1
                    : b.getInt();
            List<Integer> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(b.getInt());
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(b); // per-partition
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            }
            topics.add(new AddPartitionsToTxnRequest.TopicPartitions(name, parts));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        }
        return new AddPartitionsToTxnRequest(txnId, producerId, epoch, topics);
    }

    // ── v4 request: batched Transactions[] ───────────────────────────────
    // Order: Transactions[] { TransactionalId(string), ProducerId(int64),
    //   ProducerEpoch(int16), VerifyOnly(bool), Topics[] { Name(string),
    //   Partitions[] int32 } } — all flexible

    private static byte[] batchRequest(short version, AddPartitionsToTxnRequest req) {
        int size = 1; // numTransactions varint (1 transaction → N+1 = 2, 1 byte)
        String txnId = req.transactionalId();
        size += 1 + (txnId == null ? 0 : txnId.getBytes(StandardCharsets.UTF_8).length); // compact txnId (varint count + bytes; null → 0 bytes)
        size += 8; // ProducerId
        size += 2; // ProducerEpoch
        size += 1; // VerifyOnly varint
        size += 1; // numTopics varint
        for (AddPartitionsToTxnRequest.TopicPartitions t : req.topics()) {
            size += 1 + t.name().getBytes(StandardCharsets.UTF_8).length; // compact name
            size += 1; // numPartitions varint
            size += t.partitionIndexes().size() * 5; // int32 + endTags per partition
            size += 1; // per-topic endTags
        }
        size += 1; // per-transaction endTags
        size += 1; // top-level endTags

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeVarint(buf, 1 + 1); // KIP-482: 1 transaction → N+1 = 2
        KafkaCodecPrimitives.writeCompactString(buf, txnId);
        buf.putLong(req.producerId());
        buf.putShort(req.producerEpoch());
        KafkaCodecPrimitives.writeVarint(buf, 0); // VerifyOnly = false (spec default)
        KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1); // KIP-482: N+1
        for (AddPartitionsToTxnRequest.TopicPartitions t : req.topics()) {
            KafkaCodecPrimitives.writeCompactString(buf, t.name());
            KafkaCodecPrimitives.writeVarint(buf, t.partitionIndexes().size() + 1); // KIP-482: N+1
            for (int idx : t.partitionIndexes()) {
                buf.putInt(idx);
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // per-transaction endTags
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AddPartitionsToTxnRequest batchRequest(short version, ByteBuffer b) {
        int txnCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        if (txnCount != 1) {
            // The in-house model is a single transaction; multi-transaction batches are
            // parsed defensively (first transaction wins, extras skipped).
            // (Documented in the class javadoc — see version differences.)
        }
        AddPartitionsToTxnRequest first = null;
        for (int i = 0; i < txnCount; i++) {
            String txnId = KafkaCodecPrimitives.readCompactString(b);
            long producerId = b.getLong();
            short epoch = b.getShort();
            b.get(); // VerifyOnly varint (1 byte), read + discarded
            int topicCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            List<AddPartitionsToTxnRequest.TopicPartitions> topics = new ArrayList<>(topicCount);
            for (int j = 0; j < topicCount; j++) {
                String name = KafkaCodecPrimitives.readCompactString(b);
                int partCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
                List<Integer> parts = new ArrayList<>(partCount);
                for (int k = 0; k < partCount; k++) {
                    parts.add(b.getInt());
                    KafkaCodecPrimitives.skipTaggedFields(b); // per-partition
                }
                KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
                topics.add(new AddPartitionsToTxnRequest.TopicPartitions(name, parts));
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-transaction
            if (first == null) {
                first = new AddPartitionsToTxnRequest(txnId, producerId, epoch, topics);
            }
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        if (first == null) {
            return new AddPartitionsToTxnRequest("", -1L, (short) -1, new ArrayList<>());
        }
        return first;
    }

    // ── Response ─────────────────────────────────────────────────────────
    /**
     * Encode an AddPartitionsToTxn response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, AddPartitionsToTxnResponse resp) {
        checkVersion(version);
        if (version >= V4_BATCH_START) {
            return batchResponse(resp);
        }
        return singleResponse(version, resp);
    }

    /**
     * Decode an AddPartitionsToTxn response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param buf     the response body
     * @return the decoded response
     */
    public static AddPartitionsToTxnResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version >= V4_BATCH_START) {
            return batchResponse(buf);
        }
        return singleResponse(version, buf);
    }

    // ── v0–v3 response (v0/v1/v2 byte-identical; v3 flexible) ────────────
    // Order: ThrottleTimeMs(int32, present from v0), ResultsByTopicV3AndBelow[]
    //   { Name(string), ResultsByPartition[] { PartitionIndex(int32),
    //   PartitionErrorCode(int16) } }

    private static byte[] singleResponse(short version, AddPartitionsToTxnResponse resp) {
        boolean flexible = version >= FLEXIBLE_START;
        int size = 4; // ThrottleTimeMs
        size += 4; // numTopics (fixed-width only)
        for (AddPartitionsToTxnResponse.TopicResponse t : resp.topics()) {
            size += 4 + 2 + t.name().getBytes(StandardCharsets.UTF_8).length; // name (fixed: int16 len)
            size += 4; // numPartitions (fixed-width only)
            size += t.partitions().size() * (4 + 2); // PartitionIndex + PartitionErrorCode
            if (flexible) {
                size += 1; // per-topic endTags
                size += 1; // per-partition endTags (1 per partition)
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1); // KIP-482: N+1
        } else {
            buf.putInt(resp.topics().size());
        }
        for (AddPartitionsToTxnResponse.TopicResponse t : resp.topics()) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, t.name());
            } else {
                KafkaCodecPrimitives.writeString(buf, t.name());
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, t.partitions().size() + 1); // KIP-482: N+1
            } else {
                buf.putInt(t.partitions().size());
            }
            for (AddPartitionsToTxnResponse.PartitionResponse p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putShort(p.errorCode());
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AddPartitionsToTxnResponse singleResponse(short version, ByteBuffer b) {
        boolean flexible = version >= FLEXIBLE_START;
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int topicCount = flexible
                ? KafkaCodecPrimitives.readVarint(b) - 1 // KIP-482: N+1
                : b.getInt();
        List<AddPartitionsToTxnResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = flexible
                    ? KafkaCodecPrimitives.readCompactString(b)
                    : KafkaCodecPrimitives.readString(b);
            int partCount = flexible
                    ? KafkaCodecPrimitives.readVarint(b) - 1 // KIP-482: N+1
                    : b.getInt();
            List<AddPartitionsToTxnResponse.PartitionResponse> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new AddPartitionsToTxnResponse.PartitionResponse(b.getInt(), b.getShort()));
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(b); // per-partition
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            }
            topics.add(new AddPartitionsToTxnResponse.TopicResponse(name, parts));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        }
        return new AddPartitionsToTxnResponse(topics);
    }

    // ── v4 response: ThrottleTimeMs + ignorable ErrorCode + ResultsByTransaction[] ──
    // Order: ThrottleTimeMs(int32), ErrorCode(int16, ignorable), ResultsByTransaction[]
    //   { TransactionalId(string), TopicResults[] { Name(string),
    //   ResultsByPartition[] { PartitionIndex(int32), PartitionErrorCode(int16) } } }

    private static byte[] batchResponse(AddPartitionsToTxnResponse resp) {
        int size = 4; // ThrottleTimeMs
        size += 2; // ErrorCode (ignorable)
        size += 1; // numTransactions varint (1 → 1 byte)
        size += 1 + 1 + "txn".getBytes(StandardCharsets.UTF_8).length; // placeholder compact txnId (1 byte varint + 1 byte varint + 3)
        size += 1; // numTopicResults varint
        for (AddPartitionsToTxnResponse.TopicResponse t : resp.topics()) {
            size += 1 + 1 + t.name().getBytes(StandardCharsets.UTF_8).length; // compact name
            size += 1; // numPartitions varint
            size += t.partitions().size() * (4 + 2 + 1); // int32 + int16 + endTags per partition
            size += 1; // per-topic endTags
        }
        size += 1; // per-transaction endTags
        size += 1; // top-level endTags

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        buf.putShort((short) 0); // ErrorCode (ignorable) — 0 = NO_ERROR
        KafkaCodecPrimitives.writeVarint(buf, 1 + 1); // KIP-482: 1 transaction → N+1 = 2
        KafkaCodecPrimitives.writeCompactString(buf, "txn"); // single-transaction batch placeholder
        KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1); // KIP-482: N+1
        for (AddPartitionsToTxnResponse.TopicResponse t : resp.topics()) {
            KafkaCodecPrimitives.writeCompactString(buf, t.name());
            KafkaCodecPrimitives.writeVarint(buf, t.partitions().size() + 1); // KIP-482: N+1
            for (AddPartitionsToTxnResponse.PartitionResponse p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putShort(p.errorCode());
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // per-transaction endTags
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AddPartitionsToTxnResponse batchResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded
        b.getShort(); // ErrorCode (ignorable), read + discarded
        int txnCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        AddPartitionsToTxnResponse first = null;
        for (int i = 0; i < txnCount; i++) {
            KafkaCodecPrimitives.readCompactString(b); // TransactionalId (key), read + discarded
            int topicCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            List<AddPartitionsToTxnResponse.TopicResponse> topics = new ArrayList<>(topicCount);
            for (int j = 0; j < topicCount; j++) {
                String name = KafkaCodecPrimitives.readCompactString(b);
                int partCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
                List<AddPartitionsToTxnResponse.PartitionResponse> parts = new ArrayList<>(partCount);
                for (int k = 0; k < partCount; k++) {
                    parts.add(new AddPartitionsToTxnResponse.PartitionResponse(b.getInt(), b.getShort()));
                    KafkaCodecPrimitives.skipTaggedFields(b); // per-partition
                }
                KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
                topics.add(new AddPartitionsToTxnResponse.TopicResponse(name, parts));
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-transaction
            if (first == null) {
                first = new AddPartitionsToTxnResponse(topics);
            }
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        if (first == null) {
            return new AddPartitionsToTxnResponse(new ArrayList<>());
        }
        return first;
    }
}
