package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.EndTxnRequest;
import ssg.legoflow.messaging.kafka.protocol.EndTxnResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Codec for the Kafka EndTxn request (API key 26) and response — commit or abort an
 * in-flight transaction identified by {@code TransactionalId} + {@code ProducerId}.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline the
 * in-house client sends), while the full version range v0–v3 is encoded so a frame carrying
 * any EndTxn version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/EndTxnRequest.json} and
 * {@code EndTxnResponse.json}):
 * <ul>
 *   <li>v0–v2 (both): byte-identical. Request: {@code TransactionalId} (nullable int16
 *       string) + {@code ProducerId} (int64) + {@code ProducerEpoch} (int16) + {@code
 *       Committed} (bool, 1 byte: 1 commit / 0 abort). Response: leading {@code ThrottleTimeMs}
 *       (int32, present from v0) + {@code ErrorCode} (int16).</li>
 *   <li>v3: flexible encoding (KIP-482): the request string becomes compact (varint length + 1)
 *       and a trailing top-level {@code endTags} varint is appended to both directions. The fixed
 *       primitives ({@code ProducerId}, {@code ProducerEpoch}, {@code Committed}) are unchanged —
 *       KIP-482 compacts strings only. The 3.6.1 schema declares no tagged fields.</li>
 * </ul>
 * All fields are present from v0 in both directions, so the model maps 1:1 onto every
 * version with no version-gated appends.
 * <p>
 * The response model does not expose {@code ThrottleTimeMs}: the spec default (0) is
 * written and read + discarded.
 *
 * @since 0.1.0
 */
public final class EndTxnCodec {

    /** Spec minimum version (Kafka 2.5.0). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.0+). */
    public static final short MAX_VERSION = 3;

    /** Flexible (KIP-482) encoding starts at v3; the string becomes compact. */
    public static final short FLEXIBLE_START = 3;

    /** Version pinned for the client/broker interaction. */
    public static final short PINNED_VERSION = 0;

    private EndTxnCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "EndTxn v" + version + " not implemented (range 0–3)");
        }
    }

    /**
     * On-the-wire size of a nullable string field. Fixed-width: int16 length prefix (2 bytes)
     * + UTF-8 bytes (or nothing when null). Flexible: varint length prefix (1 byte for
     * realistic transactional-id lengths; length 0–127 for null too).
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
     * Encode an {@link EndTxnRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(EndTxnRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode an EndTxn request body at the given API version.
     *
     * @param version the API version (0–3)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, EndTxnRequest req) {
        checkVersion(version);
        return request(version, req);
    }

    /**
     * Decode an EndTxn request body at the given API version.
     *
     * @param version the API version (0–3)
     * @param buf     the request body
     * @return the decoded request
     */
    public static EndTxnRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        return request(version, buf);
    }

    // ── Request (v0–v2 byte-identical; v3 flexible) ─────────────────────
    // Order: TransactionalId(string), ProducerId(int64), ProducerEpoch(int16),
    //   Committed(bool)

    private static byte[] request(short version, EndTxnRequest req) {
        boolean flexible = version >= FLEXIBLE_START;
        String txnId = req.transactionalId();
        int size = 8; // ProducerId (int64)
        size += 2; // ProducerEpoch (int16)
        size += 1; // Committed (bool)
        size += stringSize(flexible, txnId); // TransactionalId
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
        buf.put((byte) (req.committed() ? 1 : 0)); // Committed (bool)
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static EndTxnRequest request(short version, ByteBuffer b) {
        boolean flexible = version >= FLEXIBLE_START;
        String txnId = flexible
                ? KafkaCodecPrimitives.readCompactString(b)
                : KafkaCodecPrimitives.readNullableString(b);
        long producerId = b.getLong();
        short epoch = b.getShort();
        boolean committed = b.get() == 1;
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        }
        return new EndTxnRequest(txnId, producerId, epoch, committed);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode an {@link EndTxnResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(EndTxnResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode an EndTxn response body at the given API version.
     *
     * @param version the API version (0–3)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, EndTxnResponse resp) {
        checkVersion(version);
        return response(version, resp);
    }

    /**
     * Decode an EndTxn response body at the given API version.
     *
     * @param version the API version (0–3)
     * @param buf     the response body
     * @return the decoded response
     */
    public static EndTxnResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        return response(version, buf);
    }

    // ── Response (v0–v2 byte-identical; v3 flexible) ────────────────────
    // Order: ThrottleTimeMs(int32, present from v0), ErrorCode(int16)

    private static byte[] response(short version, EndTxnResponse resp) {
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

    private static EndTxnResponse response(short version, ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        short errorCode = b.getShort();
        if (version >= FLEXIBLE_START) {
            KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        }
        return new EndTxnResponse(errorCode);
    }
}
