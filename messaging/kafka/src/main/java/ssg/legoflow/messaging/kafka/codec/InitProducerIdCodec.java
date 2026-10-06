package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.InitProducerIdRequest;
import ssg.legoflow.messaging.kafka.protocol.InitProducerIdResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Codec for the Kafka InitProducerId request (API key 22) and response — a
 * transactional producer obtains its {@code ProducerId}/{@code ProducerEpoch}.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline that
 * {@code KafkaProducer} sends), while the full version range v0–v4 is encoded so a frame
 * carrying any InitProducerId version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/InitProducerIdRequest.json} and
 * {@code InitProducerIdResponse.json}):
 * <ul>
 *   <li>v0 (request): baseline. {@code TransactionalId(nullable string),
 *       TransactionTimeoutMs(int32)}.</li>
 *   <li>v1 (request): unchanged from v0.</li>
 *   <li>v2 (request): flexible encoding (KIP-482): nullable compact string + tagged fields.</li>
 *   <li>v3 (request): adds trailing {@code ProducerId(int64), ProducerEpoch(int16)} — the
 *       producer's current id/epoch for resumption after an INVALID_PRODUCER_EPOCH error.</li>
 *   <li>v4 (request): unchanged from v3 (new PRODUCER_FENCED error code only).</li>
 *   <li>All versions (response): {@code ThrottleTimeMs(int32), ErrorCode(int16),
 *       ProducerId(int64), ProducerEpoch(int16)}; v2+ flexible (tagged fields on the
 *       top level).</li>
 * </ul>
 * Response/request fields not exposed by the in-house model (response {@code ThrottleTimeMs},
 * request v3+ {@code ProducerId}/{@code ProducerEpoch}) are written with spec defaults and
 * read + discarded.
 *
 * @since 0.1.0
 */
public final class InitProducerIdCodec {

    /** Pinned version for client/broker InitProducerId interaction (v0). */
    public static final short PINNED_VERSION = 0;

    /** Flexible-encoding versions (v2+): compact strings + tagged fields (KIP-482). */
    private static final short FLEXIBLE_START = 2;

    /** Highest API version with a defined wire format in the spec (v4). */
    private static final short MAX_VERSION = 4;

    /** Request v3+ {@code ProducerId} default — -1 (model does not expose it). */
    private static final long DEFAULT_PRODUCER_ID = -1;

    /** Request v3+ {@code ProducerEpoch} default — -1 (model does not expose it). */
    private static final short DEFAULT_PRODUCER_EPOCH = -1;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    private InitProducerIdCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "InitProducerId v" + version + " not implemented (range 0–4)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link InitProducerIdRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(InitProducerIdRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode an InitProducerId request body at the given API version.
     *
     * @param version the API version (0–4)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, InitProducerIdRequest req) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(version, req);
        }
        return flexibleRequest(version, req);
    }

    /**
     * Decode an InitProducerId request body at the given API version.
     *
     * @param version the API version (0–4)
     * @param buf     the request body
     * @return the decoded request
     */
    public static InitProducerIdRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(version, buf);
        }
        return flexibleRequest(version, buf);
    }

    // ── Fixed-width request (v0–v1) ─────────────────────────────────────
    // Order: TransactionalId(nullable string), TransactionTimeoutMs(int32),
    //   ProducerId(int64) v3+, ProducerEpoch(int16) v3+

    private static byte[] fixedRequest(short version, InitProducerIdRequest req) {
        int size = 2; // TransactionalId length prefix (nullable)
        String txnId = req.transactionalId();
        if (txnId != null) {
            size += txnId.getBytes(StandardCharsets.UTF_8).length;
        }
        size += 4; // TransactionTimeoutMs
        if (version >= 3) {
            size += 8; // ProducerId
            size += 2; // ProducerEpoch
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeNullableString(buf, txnId);
        buf.putInt(req.transactionTimeoutMs());
        if (version >= 3) {
            buf.putLong(DEFAULT_PRODUCER_ID); // ProducerId (spec default)
            buf.putShort(DEFAULT_PRODUCER_EPOCH); // ProducerEpoch (spec default)
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static InitProducerIdRequest fixedRequest(short version, ByteBuffer b) {
        String txnId = KafkaCodecPrimitives.readNullableString(b);
        int timeoutMs = b.getInt();
        if (version >= 3) {
            b.getLong(); // ProducerId, read + discarded (model does not expose it)
            b.getShort(); // ProducerEpoch, read + discarded (model does not expose it)
        }
        return new InitProducerIdRequest(txnId, timeoutMs);
    }

    // ── Flexible request (v2–v4) ────────────────────────────────────────
    // Order: TransactionalId(nullable compact string), TransactionTimeoutMs(int32),
    //   ProducerId(int64) v3+, ProducerEpoch(int16) v3+, endTags

    private static byte[] flexibleRequest(short version, InitProducerIdRequest req) {
        int size = compactNullableStringSize(req.transactionalId()); // TransactionalId
        size += 4; // TransactionTimeoutMs (fixed-width even in flexible)
        if (version >= 3) {
            size += 8; // ProducerId
            size += 2; // ProducerEpoch
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeCompactString(buf, req.transactionalId()); // nullable
        buf.putInt(req.transactionTimeoutMs());
        if (version >= 3) {
            buf.putLong(DEFAULT_PRODUCER_ID); // ProducerId (spec default)
            buf.putShort(DEFAULT_PRODUCER_EPOCH); // ProducerEpoch (spec default)
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static InitProducerIdRequest flexibleRequest(short version, ByteBuffer b) {
        String txnId = KafkaCodecPrimitives.readCompactString(b); // nullable
        int timeoutMs = b.getInt();
        if (version >= 3) {
            b.getLong(); // ProducerId, read + discarded (model does not expose it)
            b.getShort(); // ProducerEpoch, read + discarded (model does not expose it)
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new InitProducerIdRequest(txnId, timeoutMs);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link InitProducerIdResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(InitProducerIdResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode an InitProducerId response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, InitProducerIdResponse resp) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(resp);
        }
        return flexibleResponse(resp);
    }

    /**
     * Decode an InitProducerId response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param buf     the response body
     * @return the decoded response
     */
    public static InitProducerIdResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(buf);
        }
        return flexibleResponse(buf);
    }

    // ── Fixed-width response (v0–v1) ────────────────────────────────────
    // Order: ThrottleTimeMs(int32), ErrorCode(int16), ProducerId(int64), ProducerEpoch(int16)

    private static byte[] fixedResponse(InitProducerIdResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4 + 2 + 8 + 2);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS); // ThrottleTimeMs (spec default)
        buf.putShort(resp.errorCode());
        buf.putLong(resp.producerId());
        buf.putShort(resp.producerEpoch());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static InitProducerIdResponse fixedResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        short errorCode = b.getShort();
        long producerId = b.getLong();
        short producerEpoch = b.getShort();
        return new InitProducerIdResponse(errorCode, producerId, producerEpoch);
    }

    // ── Flexible response (v2–v4) ───────────────────────────────────────
    // Order: ThrottleTimeMs(int32), ErrorCode(int16), ProducerId(int64),
    //   ProducerEpoch(int16), endTags

    private static byte[] flexibleResponse(InitProducerIdResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4 + 2 + 8 + 2 + 1);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS); // ThrottleTimeMs (spec default)
        buf.putShort(resp.errorCode());
        buf.putLong(resp.producerId());
        buf.putShort(resp.producerEpoch());
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static InitProducerIdResponse flexibleResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        short errorCode = b.getShort();
        long producerId = b.getLong();
        short producerEpoch = b.getShort();
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new InitProducerIdResponse(errorCode, producerId, producerEpoch);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Size of a nullable compact string (varint 0 if null). */
    private static int compactNullableStringSize(String s) {
        if (s == null) {
            return 1; // varint 0
        }
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        return KafkaCodecPrimitives.varintSize(len + 1) + len;
    }
}
