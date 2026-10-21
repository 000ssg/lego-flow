package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.MetadataRequest;
import ssg.legoflow.messaging.kafka.protocol.MetadataResponse;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for the Metadata/Cluster Metadata request (API key 3) and response, v0–v12.
 *
 * <p>Conventions (mirroring {@link JoinGroupCodecTest}): round-trips encode at every version
 * then decode back and assert value equality, plus byte-for-byte goldens per version (generated
 * by the independent Python reference encoder {@code doc/work/metadata_golden.py}, built from
 * the frozen order tables {@code doc/spec/order/Metadata.{Request,Response}.txt}).
 * <ul>
 *   <li>Request: v0 base ({@code Topics[] { Name }} — empty array = all topics); v1+ nullable
 *       array (null = all topics, legacy int32(-1) / flexible varint 0); v4+
 *       {@code AllowAutoTopicCreation} (round-trips); v8–v10 {@code IncludeClusterAuthorizedOperations}
 *       and v8+ {@code IncludeTopicAuthorizedOperations} (written false, read + discarded);
 *       v9+ KIP-482 flexible (compact strings, N+1 varint counts, endTags); v10+ 16-byte
 *       {@code TopicId} before Name (written zeros, read + discarded).</li>
 *   <li>Response: v0 base ({@code Brokers[] { NodeId, Host, Port } + Topics[] { ErrorCode, Name,
 *       Partitions[] { ErrorCode, PartitionIndex, LeaderId, ReplicaNodes, IsrNodes } }}); v1+
 *       per-broker Rack (null), per-topic IsInternal (false) and ControllerId (-1); v2+
 *       ClusterId (null); v3+ leading ThrottleTimeMs (0); v5+ per-partition OfflineReplicas
 *       (empty); v7+ per-partition LeaderEpoch (-1); v8+ per-topic TopicAuthorizedOperations
 *       (MIN) and trailing ClusterAuthorizedOperations (MIN, removed at v11); v9+ flexible;
 *       v10+ per-topic TopicId (zeros). All of the above are read + discarded (models unchanged).</li>
 * </ul>
 */
class MetadataCodecTest {

    // ── fixtures ────────────────────────────────────────────────────────
    // Request: one topic "topic1" (golden fixture), plus all-topics (null) and two topics.
    private static final MetadataRequest REQ_ONE = new MetadataRequest(List.of("topic1"));
    private static final MetadataRequest REQ_ALL = new MetadataRequest();
    private static final MetadataRequest REQ_TWO = new MetadataRequest(List.of("alpha", "beta"));

    // Response: 1 broker 0/host1:9092, 1 topic "t1", 1 partition 0 with leader 7, replicas (7),
    // ISR (7) — matches the Python golden fixture.
    private static final MetadataResponse RESPONSE = new MetadataResponse(
            List.of(new MetadataResponse.BrokerMetadata(0, "host1", 9092)),
            List.of(new MetadataResponse.TopicMetadata((short) 0, "t1",
                    List.of(new MetadataResponse.PartitionMetadata(
                            (short) 0, 0, 7, List.of(7), List.of(7))))));

    // ── goldens (doc/work/metadata_golden.py, built from the frozen tables) ──

    /** Request v0: Topics count 1 + "topic1". */
    private static final byte[] REQ_V0 = fromHex("000000010006746f70696331");
    /** Request v0, all topics (null): empty array count 0. */
    private static final byte[] REQ_V0_ALL = fromHex("00000000");
    /** Request v1–v8, all topics (null): int32(-1) null-array marker. */
    private static final byte[] REQ_V1_ALL = fromHex("ffffffff");
    /** Request v3 = v0 (byte-identical layout, frozen table: v1-v3). */
    private static final byte[] REQ_V3 = fromHex("000000010006746f70696331");
    /** Request v4 = v0 + AllowAutoTopicCreation(false, model default). */
    private static final byte[] REQ_V4 = fromHex("000000010006746f7069633100");
    /** Request v8 = v4 + IncludeClusterAuthorizedOperations(false) + IncludeTopicAuthorizedOperations(false). */
    private static final byte[] REQ_V8 = fromHex("000000010006746f70696331000000");
    /** Request v8, two topics: alpha, beta. */
    private static final byte[] REQ_V8_TWOTOPIC = fromHex("000000020005616c706861000462657461000000");
    /** Request v9 (flexible): varint count(1)=2, compact "topic1", per-topic endTags(0), allow(0), cluster(0), topic(0), top-level endTags(0). */
    private static final byte[] REQ_V9 = fromHex("0207746f706963310000000000");
    /** Request v10 = v9 + 16 zero-byte TopicId before Name. */
    private static final byte[] REQ_V10 = fromHex("020000000000000000000000000000000007746f706963310000000000");
    /** Request v11 = v10 minus IncludeClusterAuthorizedOperations. */
    private static final byte[] REQ_V11 = fromHex("020000000000000000000000000000000007746f7069633100000000");
    /** Request v12 = v11 (wire-identical layout). */
    private static final byte[] REQ_V12 = fromHex("020000000000000000000000000000000007746f7069633100000000");
    /** Response v0: 1 broker + 1 topic + 1 partition. */
    private static final byte[] RSP_V0 = fromHex("00000001000000000005686f7374310000238400000001000000027431000000010000000000000000000700000001000000070000000100000007");
    /** Response v1 = v0 + Rack(null) + IsInternal(false) + ControllerId(-1). */
    private static final byte[] RSP_V1 = fromHex("00000001000000000005686f73743100002384ffffffffffff0000000100000002743100000000010000000000000000000700000001000000070000000100000007");
    /** Response v2 = v1 + ClusterId(null). */
    private static final byte[] RSP_V2 = fromHex("00000001000000000005686f73743100002384ffffffffffffffff0000000100000002743100000000010000000000000000000700000001000000070000000100000007");
    /** Response v3 = v2 + leading ThrottleTimeMs(0). */
    private static final byte[] RSP_V3 = fromHex("0000000000000001000000000005686f73743100002384ffffffffffffffff0000000100000002743100000000010000000000000000000700000001000000070000000100000007");
    /** Response v3 with ThrottleTimeMs(250) — pinning the leading field. */
    private static final byte[] RSP_V3_THROTTLED = fromHex("000000fa00000001000000000005686f73743100002384ffffffffffffffff0000000100000002743100000000010000000000000000000700000001000000070000000100000007");
    /** Response v4 = v3 (byte-identical layout, frozen table: v3-v4). */
    private static final byte[] RSP_V4 = fromHex("0000000000000001000000000005686f73743100002384ffffffffffffffff0000000100000002743100000000010000000000000000000700000001000000070000000100000007");
    /** Response v5 = v3/v4 + OfflineReplicas count(0) after IsrNodes. */
    private static final byte[] RSP_V5 = fromHex("0000000000000001000000000005686f73743100002384ffffffffffffffff000000010000000274310000000001000000000000000000070000000100000007000000010000000700000000");
    /** Response v6 = v5 (byte-identical layout, frozen table: v5-v6). */
    private static final byte[] RSP_V6 = fromHex("0000000000000001000000000005686f73743100002384ffffffffffffffff000000010000000274310000000001000000000000000000070000000100000007000000010000000700000000");
    /** Response v7 = v5/v6 + LeaderEpoch(-1) after LeaderId. */
    private static final byte[] RSP_V7 = fromHex("0000000000000001000000000005686f73743100002384ffffffffffffffff00000001000000027431000000000100000000000000000007ffffffff0000000100000007000000010000000700000000");
    /** Response v8 = v7 + TopicAuthorizedOperations(MIN) + trailing ClusterAuthorizedOperations(MIN). */
    private static final byte[] RSP_V8 = fromHex("0000000000000001000000000005686f73743100002384ffffffffffffffff00000001000000027431000000000100000000000000000007ffffffff00000001000000070000000100000007000000008000000080000000");
    /** Response v8 with TopicAuthorizedOperations(4) + ClusterAuthorizedOperations(16). */
    private static final byte[] RSP_V8_AUTHS = fromHex("0000000000000001000000000005686f73743100002384ffffffffffffffff00000001000000027431000000000100000000000000000007ffffffff00000001000000070000000100000007000000000000000400000010");
    /** Response v9 (flexible) — compact strings, N+1 varint counts, five endTags(0) trailers. */
    private static final byte[] RSP_V9 = fromHex("00000000020000000006686f73743100002384000000ffffffff020000037431000200000000000000000007ffffffff02000000070200000007010080000000008000000000");
    /** Response v10 = v9 + 16 zero-byte TopicId after Name. */
    private static final byte[] RSP_V10 = fromHex("00000000020000000006686f73743100002384000000ffffffff02000003743100000000000000000000000000000000000200000000000000000007ffffffff02000000070200000007010080000000008000000000");
    /** Response v11 = v10 minus ClusterAuthorizedOperations. */
    private static final byte[] RSP_V11 = fromHex("00000000020000000006686f73743100002384000000ffffffff02000003743100000000000000000000000000000000000200000000000000000007ffffffff020000000702000000070100800000000000");
    /** Response v12 = v11 (wire-identical layout). */
    private static final byte[] RSP_V12 = fromHex("00000000020000000006686f73743100002384000000ffffffff02000003743100000000000000000000000000000000000200000000000000000007ffffffff020000000702000000070100800000000000");

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    // ── request round-trips ─────────────────────────────────────────────

    @Test
    void requestRoundTripAllVersions() {
        for (short v = MetadataCodec.MIN_VERSION; v <= MetadataCodec.MAX_VERSION; v++) {
            byte[] body = MetadataCodec.encodeRequest(v, REQ_ONE);
            MetadataRequest decoded = MetadataCodec.decodeRequest(v, ByteBuffer.wrap(body));
            assertEquals(List.of("topic1"), decoded.topics(), "topics at v" + v);
            assertEquals(false, decoded.allowAutoTopicCreation(), "allow at v" + v + " (model default, absent pre-v4)");
            assertArrayEquals(body, MetadataCodec.encodeRequest(v, decoded), "v" + v + " re-encode");
        }
    }

    @Test
    void requestRoundTripTwoTopicsAllVersions() {
        for (short v = MetadataCodec.MIN_VERSION; v <= MetadataCodec.MAX_VERSION; v++) {
            byte[] body = MetadataCodec.encodeRequest(v, REQ_TWO);
            MetadataRequest decoded = MetadataCodec.decodeRequest(v, ByteBuffer.wrap(body));
            assertEquals(List.of("alpha", "beta"), decoded.topics(), "topics at v" + v);
            assertArrayEquals(body, MetadataCodec.encodeRequest(v, decoded), "v" + v + " re-encode");
        }
    }

    @Test
    void requestRoundTripNullTopicsAllVersions() {
        for (short v = MetadataCodec.MIN_VERSION; v <= MetadataCodec.MAX_VERSION; v++) {
            byte[] body = MetadataCodec.encodeRequest(v, REQ_ALL);
            MetadataRequest decoded = MetadataCodec.decodeRequest(v, ByteBuffer.wrap(body));
            assertEquals(null, decoded.topics(), "null topics at v" + v);
            assertArrayEquals(body, MetadataCodec.encodeRequest(v, decoded), "v" + v + " re-encode");
        }
    }

    @Test
    void requestAllowAutoTopicCreationRoundTripsFromV4() {
        var req = new MetadataRequest(List.of("topic1"), true);
        for (short v = 4; v <= MetadataCodec.MAX_VERSION; v++) {
            byte[] body = MetadataCodec.encodeRequest(v, req);
            assertEquals(true, MetadataCodec.decodeRequest(v, ByteBuffer.wrap(body)).allowAutoTopicCreation(),
                    "allow=true round-trips at v" + v);
        }
    }

    @Test
    void requestV0EmptyListDecodesAsAllTopics() {
        // spec 3.6.1: at v0 an empty array indicates "request metadata for all topics"
        byte[] body = MetadataCodec.encodeRequest((short) 0, new MetadataRequest(List.of()));
        assertArrayEquals(REQ_V0_ALL, body);
        assertEquals(null, MetadataCodec.decodeRequest((short) 0, ByteBuffer.wrap(body)).topics());
    }

    // ── response round-trips ────────────────────────────────────────────

    @Test
    void responseRoundTripAllVersions() {
        for (short v = MetadataCodec.MIN_VERSION; v <= MetadataCodec.MAX_VERSION; v++) {
            byte[] body = MetadataCodec.encodeResponse(v, RESPONSE);
            MetadataResponse decoded = MetadataCodec.decodeResponse(v, ByteBuffer.wrap(body));
            assertResponseRoundTrip(RESPONSE, decoded, "v" + v);
            assertArrayEquals(body, MetadataCodec.encodeResponse(v, decoded), "v" + v + " re-encode");
        }
    }

    @Test
    void responseRoundTripEmptyTopicsAndPartitions() {
        var resp = new MetadataResponse(
                List.of(new MetadataResponse.BrokerMetadata(7, "h", 1)),
                List.of(new MetadataResponse.TopicMetadata((short) 17, "no-such", List.of())));
        for (short v = MetadataCodec.MIN_VERSION; v <= MetadataCodec.MAX_VERSION; v++) {
            byte[] body = MetadataCodec.encodeResponse(v, resp);
            MetadataResponse decoded = MetadataCodec.decodeResponse(v, ByteBuffer.wrap(body));
            assertResponseRoundTrip(resp, decoded, "v" + v);
        }
    }

    @Test
    void responseRoundTripMultipleBrokers() {
        var resp = new MetadataResponse(
                List.of(
                        new MetadataResponse.BrokerMetadata(0, "host1", 9092),
                        new MetadataResponse.BrokerMetadata(1, "host2", 9093)),
                List.of());
        for (short v = MetadataCodec.MIN_VERSION; v <= MetadataCodec.MAX_VERSION; v++) {
            byte[] body = MetadataCodec.encodeResponse(v, resp);
            MetadataResponse decoded = MetadataCodec.decodeResponse(v, ByteBuffer.wrap(body));
            assertResponseRoundTrip(resp, decoded, "v" + v);
        }
    }

    private static void assertResponseRoundTrip(MetadataResponse expected, MetadataResponse actual, String msg) {
        assertEquals(expected.brokers().size(), actual.brokers().size(), msg + " brokers size");
        for (int i = 0; i < expected.brokers().size(); i++) {
            var eb = expected.brokers().get(i);
            var ab = actual.brokers().get(i);
            assertEquals(eb.nodeId(), ab.nodeId(), msg + " broker " + i + " nodeId");
            assertEquals(eb.host(), ab.host(), msg + " broker " + i + " host");
            assertEquals(eb.port(), ab.port(), msg + " broker " + i + " port");
        }
        assertEquals(expected.topics().size(), actual.topics().size(), msg + " topics size");
        for (int i = 0; i < expected.topics().size(); i++) {
            var et = expected.topics().get(i);
            var at = actual.topics().get(i);
            assertEquals(et.errorCode(), at.errorCode(), msg + " topic " + i + " error");
            assertEquals(et.name(), at.name(), msg + " topic " + i + " name");
            assertEquals(et.partitions().size(), at.partitions().size(), msg + " topic " + i + " partitions size");
            for (int j = 0; j < et.partitions().size(); j++) {
                var ep = et.partitions().get(j);
                var ap = at.partitions().get(j);
                assertEquals(ep.errorCode(), ap.errorCode(), msg + " partition error");
                assertEquals(ep.partitionIndex(), ap.partitionIndex(), msg + " partition index");
                assertEquals(ep.leaderId(), ap.leaderId(), msg + " leader");
                assertEquals(ep.replicaIds(), ap.replicaIds(), msg + " replicas");
                assertEquals(ep.isrIds(), ap.isrIds(), msg + " isrs");
            }
        }
    }

    // ── byte-for-byte goldens (independently generated) ─────────────────

    @Test
    void requestGoldens() {
        assertArrayEquals(REQ_V0, MetadataCodec.encodeRequest((short) 0, REQ_ONE), "v0");
        assertArrayEquals(REQ_V3_GOLDEN(REQ_V0), MetadataCodec.encodeRequest((short) 3, REQ_ONE), "v3 == v0 layout");
        assertArrayEquals(REQ_V4, MetadataCodec.encodeRequest((short) 4, REQ_ONE), "v4");
        assertArrayEquals(REQ_V8, MetadataCodec.encodeRequest((short) 8, REQ_ONE), "v8");
        assertArrayEquals(REQ_V8_TWOTOPIC, MetadataCodec.encodeRequest((short) 8, REQ_TWO), "v8 two topics");
        assertArrayEquals(REQ_V9, MetadataCodec.encodeRequest((short) 9, REQ_ONE), "v9 flexible");
        assertArrayEquals(REQ_V10, MetadataCodec.encodeRequest((short) 10, REQ_ONE), "v10 + TopicId");
        assertArrayEquals(REQ_V11, MetadataCodec.encodeRequest((short) 11, REQ_ONE), "v11");
        assertArrayEquals(REQ_V11, MetadataCodec.encodeRequest((short) 12, REQ_ONE), "v12 == v11 layout");
    }

    private static byte[] REQ_V3_GOLDEN(byte[] v0) {
        // v1–v3 are byte-identical to v0 (frozen table: v1-v3 -> Topics:Topics[*].Name)
        return v0;
    }

    @Test
    void requestNullTopicsGoldens() {
        assertArrayEquals(REQ_V0_ALL, MetadataCodec.encodeRequest((short) 0, REQ_ALL), "v0 empty array");
        // v1-v3: int32(-1) marker only
        assertArrayEquals(REQ_V1_ALL, MetadataCodec.encodeRequest((short) 1, REQ_ALL), "v1 int32(-1)");
        for (short v = 2; v <= 3; v++) {
            assertArrayEquals(REQ_V1_ALL, MetadataCodec.encodeRequest(v, REQ_ALL), "v" + v + " int32(-1)");
        }
        // v4-v7: int32(-1) + AllowAutoTopicCreation(false)
        byte[] nullV4 = appendByte(REQ_V1_ALL, (byte) 0);
        for (short v = 4; v <= 7; v++) {
            assertArrayEquals(nullV4, MetadataCodec.encodeRequest(v, REQ_ALL), "v" + v + " int32(-1)+allow");
        }
        // v8 (legacy): int32(-1) + allow + cluster + topic
        byte[] nullV8 = new byte[7];
        System.arraycopy(REQ_V1_ALL, 0, nullV8, 0, 4);
        nullV8[4] = 0; nullV8[5] = 0; nullV8[6] = 0;
        assertArrayEquals(nullV8, MetadataCodec.encodeRequest((short) 8, REQ_ALL), "v8 int32(-1)+3 bools");
        // v9-v12 flexible null array = varint(0) + trailing bools + top-level endTags
        assertArrayEquals(fromHex("0000000000"), MetadataCodec.encodeRequest((short) 9, REQ_ALL), "v9 varint(0)+cluster+topic+endTags");
        assertArrayEquals(fromHex("0000000000"), MetadataCodec.encodeRequest((short) 10, REQ_ALL), "v10");
        assertArrayEquals(fromHex("00000000"), MetadataCodec.encodeRequest((short) 11, REQ_ALL), "v11");
        assertArrayEquals(fromHex("00000000"), MetadataCodec.encodeRequest((short) 12, REQ_ALL), "v12");
    }

    private static byte[] appendByte(byte[] arr, byte b) {
        byte[] out = new byte[arr.length + 1];
        System.arraycopy(arr, 0, out, 0, arr.length);
        out[arr.length] = b;
        return out;
    }

    @Test
    void responseGoldens() {
        assertArrayEquals(RSP_V0, MetadataCodec.encodeResponse((short) 0, RESPONSE), "v0");
        assertArrayEquals(RSP_V1, MetadataCodec.encodeResponse((short) 1, RESPONSE), "v1");
        assertArrayEquals(RSP_V2, MetadataCodec.encodeResponse((short) 2, RESPONSE), "v2");
        assertArrayEquals(RSP_V3, MetadataCodec.encodeResponse((short) 3, RESPONSE), "v3");
        assertArrayEquals(RSP_V3, MetadataCodec.encodeResponse((short) 4, RESPONSE), "v4 == v3 layout");
        assertArrayEquals(RSP_V5, MetadataCodec.encodeResponse((short) 5, RESPONSE), "v5");
        assertArrayEquals(RSP_V5, MetadataCodec.encodeResponse((short) 6, RESPONSE), "v6 == v5 layout");
        assertArrayEquals(RSP_V7, MetadataCodec.encodeResponse((short) 7, RESPONSE), "v7");
        assertArrayEquals(RSP_V8, MetadataCodec.encodeResponse((short) 8, RESPONSE), "v8");
        assertArrayEquals(RSP_V9, MetadataCodec.encodeResponse((short) 9, RESPONSE), "v9 flexible");
        assertArrayEquals(RSP_V10, MetadataCodec.encodeResponse((short) 10, RESPONSE), "v10 + TopicId");
        assertArrayEquals(RSP_V11, MetadataCodec.encodeResponse((short) 11, RESPONSE), "v11");
        assertArrayEquals(RSP_V11, MetadataCodec.encodeResponse((short) 12, RESPONSE), "v12 == v11 layout");
    }

    @Test
    void responsePerVersionDeltas() {
        // request: v4 +1 (Allow), v8 +2 (bools), v10 +16 (TopicId), v11 -1 (cluster bool)
        assertEquals(1, MetadataCodec.encodeRequest((short) 4, REQ_ONE).length -
                MetadataCodec.encodeRequest((short) 3, REQ_ONE).length, "v4 adds AllowAutoTopicCreation");
        assertEquals(2, MetadataCodec.encodeRequest((short) 8, REQ_ONE).length -
                MetadataCodec.encodeRequest((short) 7, REQ_ONE).length, "v8 adds two bools");
        assertEquals(16, MetadataCodec.encodeRequest((short) 10, REQ_ONE).length -
                MetadataCodec.encodeRequest((short) 9, REQ_ONE).length, "v10 adds TopicId");
        assertEquals(-1, MetadataCodec.encodeRequest((short) 11, REQ_ONE).length -
                MetadataCodec.encodeRequest((short) 10, REQ_ONE).length, "v11 drops cluster bool");
        // response: v1 +7 (Rack+IsInternal+ControllerId), v2 +2 (ClusterId), v3 +4 (Throttle),
        // v5 +4 (OfflineReplicas count), v7 +4 (LeaderEpoch), v8 +8 (two auth int32)
        assertEquals(7, RSP_V1.length - RSP_V0.length, "v1 delta");
        assertEquals(2, RSP_V2.length - RSP_V1.length, "v2 delta");
        assertEquals(4, RSP_V3.length - RSP_V2.length, "v3 delta");
        assertEquals(0, RSP_V4.length - RSP_V3.length, "v4 == v3");
        assertEquals(4, RSP_V5.length - RSP_V4.length, "v5 delta");
        assertEquals(4, RSP_V7.length - RSP_V5.length, "v7 delta (v6 == v5)");
        assertEquals(8, RSP_V8.length - RSP_V7.length, "v8 delta");
    }

    // ── guards + truncation ─────────────────────────────────────────────

    @Test
    void requestVersionGuard() {
        assertThrows(CodecNotImplementedException.class,
                () -> MetadataCodec.encodeRequest((short) -1, REQ_ONE));
        assertThrows(CodecNotImplementedException.class,
                () -> MetadataCodec.encodeRequest((short) 13, REQ_ONE));
        assertThrows(CodecNotImplementedException.class,
                () -> MetadataCodec.decodeRequest((short) 13, ByteBuffer.wrap(REQ_V0)));
    }

    @Test
    void responseVersionGuard() {
        assertThrows(CodecNotImplementedException.class,
                () -> MetadataCodec.encodeResponse((short) -1, RESPONSE));
        assertThrows(CodecNotImplementedException.class,
                () -> MetadataCodec.encodeResponse((short) 13, RESPONSE));
        assertThrows(CodecNotImplementedException.class,
                () -> MetadataCodec.decodeResponse((short) 13, ByteBuffer.wrap(RSP_V0)));
    }

    @Test
    void truncatedRequestThrows() {
        ByteBuffer truncated = ByteBuffer.wrap(REQ_V0, 0, 6);
        assertThrows(BufferUnderflowException.class,
                () -> MetadataCodec.decodeRequest((short) 0, truncated));
    }

    @Test
    void truncatedResponseThrows() {
        ByteBuffer truncated = ByteBuffer.wrap(RSP_V0, 0, 10);
        assertThrows(BufferUnderflowException.class,
                () -> MetadataCodec.decodeResponse((short) 0, truncated));
    }

    // ── version constants ───────────────────────────────────────────────

    @Test
    void versionConstants() {
        assertEquals(0, MetadataCodec.MIN_VERSION);
        assertEquals(12, MetadataCodec.MAX_VERSION);
        assertEquals(9, MetadataCodec.FLEXIBLE_START);
    }
}
