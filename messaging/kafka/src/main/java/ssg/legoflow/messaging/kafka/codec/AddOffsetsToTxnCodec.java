package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.AddOffsetsToTxnRequest;
import ssg.legoflow.messaging.kafka.protocol.AddOffsetsToTxnResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Codec for the Kafka AddOffsetsToTxn request (API key 25) and response — register a
 * consumer group's {@code __consumer_offsets} partition with an ongoing transaction so
 * transactional offset commits can be performed via {@code TxnOffsetCommit}.
 * <p>
 * Client/broker interaction is versioned from v0 (the baseline the
 * in-house client sends), while the full version range v0–v3 is encoded so a frame carrying
 * any AddOffsetsToTxn version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/AddOffsetsToTxnRequest.json} and
 * {@code AddOffsetsToTxnResponse.json}):
 * <ul>
 *   <li>v0–v2 (both): byte-identical. Request: {@code TransactionalId} (nullable int16
 *       string) + {@code ProducerId} (int64) + {@code ProducerEpoch} (int16) + {@code
 *       GroupId} (nullable int16 string). Response: leading {@code ThrottleTimeMs} (int32,
 *       present from v0) + {@code ErrorCode} (int16).</li>
 *   <li>v3: flexible encoding (KIP-482): compact strings, top-level tagged-fields end
 *       marker. Fixed-width primitives are unchanged; the only structural difference is
 *       that the two request strings become compact (varint length + 1) and a trailing
 *       top-level {@code endTags} varint is appended to both directions.</li>
 * </ul>
 * All fields are present from v0 in both directions, so the model maps 1:1 onto every
 * version with no version-gated appends.
 * <p>
 * The response model does not expose {@code ThrottleTimeMs}: the spec default (0) is
 * written and read + discarded.
 *
 * @since 0.1.0
 */
public final class AddOffsetsToTxnCodec {

    /** Spec minimum version (Kafka 2.5.0). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.0+). */
    public static final short MAX_VERSION = 3;

    /** Flexible (KIP-482) encoding starts at v3; strings become compact. */
    public static final short FLEXIBLE_START = 3;

    /** Version pinned for the client/broker interaction. */

    private AddOffsetsToTxnCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "AddOffsetsToTxn v" + version + " not implemented (range 0–3)");
        }
    }

    /**
     * On-the-wire size of a nullable string field. Fixed-width: int16 length prefix (2 bytes)
     * + UTF-8 bytes (or nothing when null). Flexible: varint length prefix (1 byte for
     * realistic group-id lengths; length 0–127 for null too).
     */
    private static int stringSize(boolean flexible, String s) {
        if (flexible) {
            int len = s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
            return KafkaCodecPrimitives.varintSize(len + (s == null ? 0 : 1));
        }
        return 2 + (s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length);
    }

    // ── Request ─────────────────────────────────────────────────────────
    /**
     * Encode an AddOffsetsToTxn request body at the given API version.
     *
     * @param version the API version (0–3)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, AddOffsetsToTxnRequest req) {
        checkVersion(version);
        return request(version, req);
    }

    /**
     * Decode an AddOffsetsToTxn request body at the given API version.
     *
     * @param version the API version (0–3)
     * @param buf     the request body
     * @return the decoded request
     */
    public static AddOffsetsToTxnRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        return request(version, buf);
    }

    // ── Request (v0–v2 byte-identical; v3 flexible) ─────────────────────
    // Order: TransactionalId(string), ProducerId(int64), ProducerEpoch(int16),
    //   GroupId(string)

    private static byte[] request(short version, AddOffsetsToTxnRequest req) {
        boolean flexible = version >= FLEXIBLE_START;
        String txnId = req.transactionalId();
        String groupId = req.groupId();
        int size = 8; // ProducerId (int64)
        size += 2; // ProducerEpoch (int16)
        size += stringSize(flexible, txnId);   // TransactionalId
        size += stringSize(flexible, groupId); // GroupId
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (flexible) {
            KafkaCodecPrimitives.writeCompactString(buf, txnId);
        } else {
            KafkaCodecPrimitives.writeNullableString(buf, txnId);
        }
        buf.putLong(req.producerId());
        buf.putShort(req.producerEpoch());
        if (flexible) {
            KafkaCodecPrimitives.writeCompactString(buf, groupId);
        } else {
            KafkaCodecPrimitives.writeNullableString(buf, groupId);
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AddOffsetsToTxnRequest request(short version, ByteBuffer b) {
        boolean flexible = version >= FLEXIBLE_START;
        String txnId = flexible
                ? KafkaCodecPrimitives.readCompactString(b)
                : KafkaCodecPrimitives.readNullableString(b);
        long producerId = b.getLong();
        short epoch = b.getShort();
        String groupId = flexible
                ? KafkaCodecPrimitives.readCompactString(b)
                : KafkaCodecPrimitives.readNullableString(b);
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        }
        return new AddOffsetsToTxnRequest(txnId, producerId, epoch, groupId);
    }

    // ── Response ─────────────────────────────────────────────────────────
    /**
     * Encode an AddOffsetsToTxn response body at the given API version.
     *
     * @param version the API version (0–3)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, AddOffsetsToTxnResponse resp) {
        checkVersion(version);
        return response(version, resp);
    }

    /**
     * Decode an AddOffsetsToTxn response body at the given API version.
     *
     * @param version the API version (0–3)
     * @param buf     the response body
     * @return the decoded response
     */
    public static AddOffsetsToTxnResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        return response(version, buf);
    }

    // ── Response (v0–v2 byte-identical; v3 flexible) ────────────────────
    // Order: ThrottleTimeMs(int32, present from v0), ErrorCode(int16)

    private static byte[] response(short version, AddOffsetsToTxnResponse resp) {
        boolean flexible = version >= FLEXIBLE_START;
        int size = 6; // ThrottleTimeMs (int32) + ErrorCode (int16)
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(0); // ThrottleTimeMs — spec default, model does not expose it
        buf.putShort(resp.errorCode());
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AddOffsetsToTxnResponse response(short version, ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        short errorCode = b.getShort();
        if (version >= FLEXIBLE_START) {
            KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        }
        return new AddOffsetsToTxnResponse(errorCode);
    }
}
