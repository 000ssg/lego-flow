package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.FetchRequest;
import ssg.legoflow.messaging.kafka.protocol.FetchResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fetch (API key 1) v0 unit tests — Phase 6a, Record I/O, second API.
 *
 * <p>Layout expectations are taken from the Kafka 3.6.1 schema
 * ({@code doc/spec/message/Fetch{Request,Response}.json}):
 * <ul>
 *   <li>Request v0: ReplicaId(int32, -1 for consumer) + MaxWaitMs(int32) + MinBytes(int32)
 *       + Topics[](Topic(string16) + Partitions[](Partition(int32) + FetchOffset(int64) +
 *       PartitionMaxBytes(int32))). <b>No</b> MaxBytes (v3+), IsolationLevel (v4+),
 *       SessionId/SessionEpoch (v7+), CurrentLeaderEpoch (v9+), RackId (v11+), ClusterId
 *       (v12+, tagged), TopicId (v13+), LastFetchedEpoch (v12+), ForgottenTopicsData (v7+),
 *       ReplicaState (v15+, tagged). The earlier inline façade layout wrote MaxBytes while
 *       omitting ReplicaId — a v3-shaped body under a v0 frame; v0 here is spec-correct.</li>
 *   <li>Response v0: Responses[](Topic(string16) + Partitions[](PartitionIndex(int32) +
 *       ErrorCode(int16) + HighWatermark(int64) + Records(Nullable bytes))). <b>No</b>
 *       ThrottleTimeMs (v1+), top-level ErrorCode/SessionId (v7+), LastStableOffset /
 *       AbortedTransactions (v4+), LogStartOffset (v5+), PreferredReadReplica (v11+).</li>
 *   <li>v1–v2 request: byte-identical to v0 (spec: "Version 1 is the same as version 0";
 *       v2 is the first version handling message format v1 — no field change).</li>
 *   <li>Response v1: the v0 layout + a leading ThrottleTimeMs(int32) — dedicated methods.</li>
 *   <li>Response v2: unchanged — wire-identical to v1, served by the v1 methods.</li>
 *   <li>Request v3: the v0 layout with MaxBytes(int32) inserted after MinBytes —
 *       dedicated methods (47-byte reference fixture).</li>
 *   <li>Response v3: unchanged — wire-identical to v1/v2 (v3 changes the request only),
 *       served by the v1 methods.</li>
 *   <li>Request v4: v3 layout + IsolationLevel(int8) after MaxBytes — dedicated methods.</li>
 *   <li>Response v4: v1 layout + per-partition LastStableOffset(int64) +
 *       AbortedTransactions after HighWatermark — dedicated methods.</li>
 *   <li>Request v5: v4 layout + per-partition LogStartOffset(int64) after FetchOffset —
 *       dedicated methods (56-byte reference fixture).</li>
 *   <li>Response v5: v4 layout + per-partition LogStartOffset(int64) after LastStableOffset —
 *       dedicated methods (75-byte reference fixture).</li>
 *   <li>v6: unchanged version — request and response are wire-identical to v5
 *       (no field change); both directions fall through to the v5 methods.</li>
 *   <li>Request v7: v5 layout + SessionId(int32) + SessionEpoch(int32) after
 *       IsolationLevel + trailing ForgottenTopicsData[](Topic(string) + Partitions[]int32)
 *       after Topics — dedicated methods.</li>
 *   <li>Response v7: v5 layout + top-level ErrorCode(int16) + SessionId(int32) after
 *       ThrottleTimeMs — dedicated methods (per-partition layout unchanged).</li>
 *   <li>v8: unchanged version — request and response are wire-identical to v7
 *       (no field change); both directions fall through to the v7 methods.</li>
 *   <li>Request v9: v7 layout + per-partition CurrentLeaderEpoch(int32) after Partition —
 *       dedicated methods. The response is wire-identical to v7/v8 (v9 changes the
 *       request only) and falls through to the v7 response methods.</li>
 *   <li>v10: unchanged version — request is wire-identical to v9, response is
 *       wire-identical to v7/v8/v9 (no field change in either schema); the request
 *       falls through to the v9 methods, the response to the v7 methods.</li>
 *   <li>Request v11: v9 layout + trailing RackId(string) after ForgottenTopicsData —
 *       dedicated methods. The response is v7 layout + per-partition
 *       PreferredReadReplica(int32) after AbortedTransactions — dedicated methods.</li>
 *   <li>Request/response v12+ throw {@link CodecNotImplementedException} until their own
 *       sub-task rows land (v12 switches to flexible encoding).</li>
 * </ul>
 *
 * <p>All requests/responses in the v9/v10 sections below are constructed with the hybrid
 * {@code builder()} API on the {@code FetchRequest}/{@code FetchResponse} records (one named
 * method per field, spec absent-defaults, {@code build()} delegating to the canonical
 * constructor) — the approach adopted for the request/response records.
 */
class FetchCodecTest {

    @Nested
    @DisplayName("Fetch request (key 1)")
    class Request {

        @Test
        @DisplayName("v0 request round-trips with all fields")
        void v0RoundTrip() {
            var req = new FetchRequest(-1, 500, 1, 1048576,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536))),
                            new FetchRequest.TopicFetch("other", List.of())));

            byte[] body = FetchCodec.encodeRequest((short) 0, req);
            var decoded = FetchCodec.decodeRequest((short) 0, ByteBuffer.wrap(body));

            assertEquals(-1, decoded.replicaId(), "ReplicaId round-trip");
            assertEquals(500, decoded.maxWaitMs(), "MaxWaitMs round-trip");
            assertEquals(1, decoded.minBytes(), "MinBytes round-trip");
            assertEquals(2, decoded.topics().size(), "topic count");
            assertEquals("topic", decoded.topics().get(0).name());
            assertEquals(1, decoded.topics().get(0).partitions().size());
            assertEquals(0, decoded.topics().get(0).partitions().get(0).partition());
            assertEquals(10L, decoded.topics().get(0).partitions().get(0).fetchOffset());
            assertEquals(65536, decoded.topics().get(0).partitions().get(0).partitionMaxBytes());
            assertEquals("other", decoded.topics().get(1).name());
            assertEquals(0, decoded.topics().get(1).partitions().size(), "empty partition list");
            // MaxBytes is a v3+ field — the carried value is not on the v0 wire and is
            // decoded as 0.
            assertEquals(0, decoded.maxBytes(), "MaxBytes (v3+) is absent from the v0 body");
        }

        @Test
        @DisplayName("v0 request has the exact 43-byte spec wire layout")
        void v0ExactBytes() {
            var req = new FetchRequest(-1, 500, 1, 1048576,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));

            byte[] body = FetchCodec.encodeRequest((short) 0, req);
            // 4 (replicaId) + 4 (maxWaitMs) + 4 (minBytes) + 4 (topic count)
            // + 2 (name len) + 5 (name) + 4 (partition count)
            // + 4 (partition) + 8 (fetchOffset) + 4 (partitionMaxBytes) = 43
            assertEquals(43, body.length, "exact v0 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(-1, buf.getInt(), "ReplicaId int32");
            assertEquals(500, buf.getInt(), "MaxWaitMs int32");
            assertEquals(1, buf.getInt(), "MinBytes int32");
            assertEquals(1, buf.getInt(), "Topics count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "Partition int32");
            assertEquals(10L, buf.getLong(), "FetchOffset int64");
            assertEquals(65536, buf.getInt(), "PartitionMaxBytes int32");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v0 request omits MaxBytes (v3+) regardless of the carried value")
        void v0MaxBytesNotOnTheWire() {
            var topics = List.of(new FetchRequest.TopicFetch("topic", List.of(
                    new FetchRequest.PartitionFetch(0, 10L, 65536))));
            var noMaxBytes = new FetchRequest(-1, 500, 1, 0, topics);
            var withMaxBytes = new FetchRequest(-1, 500, 1, 1048576, topics);

            assertArrayEquals(FetchCodec.encodeRequest((short) 0, noMaxBytes),
                    FetchCodec.encodeRequest((short) 0, withMaxBytes),
                    "MaxBytes (v3+) must not appear in the v0 body");
        }

        @Test
        @DisplayName("v1/v2 request is byte-identical to v0 (spec: no field change)")
        void v1v2ByteIdenticalToV0() {
            var req = new FetchRequest(-1, 500, 1, 1048576,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));

            byte[] v0 = FetchCodec.encodeRequest((short) 0, req);
            byte[] v1 = FetchCodec.encodeRequest((short) 1, req);
            byte[] v2 = FetchCodec.encodeRequest((short) 2, req);

            assertArrayEquals(v0, v1, "v1 request must be byte-identical to v0");
            assertArrayEquals(v0, v2, "v2 request must be byte-identical to v0");

            var decoded = FetchCodec.decodeRequest((short) 2, ByteBuffer.wrap(v2));
            assertEquals(-1, decoded.replicaId());
            assertEquals(500, decoded.maxWaitMs());
        }
    }

    @Nested
    @DisplayName("Fetch response (key 1)")
    class Response {

        @Test
        @DisplayName("v0 response round-trips with all fields")
        void v0RoundTrip() {
            var resp = new FetchResponse(5, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L, new byte[]{1, 2}))),
                    new FetchResponse.TopicResponse("other", List.of(
                            new FetchResponse.PartitionResponse(1, (short) -1, 42L, null)))));

            byte[] body = FetchCodec.encodeResponse((short) 0, resp);
            var decoded = FetchCodec.decodeResponse((short) 0, ByteBuffer.wrap(body));

            assertEquals(2, decoded.topics().size(), "topic count");
            assertEquals("topic", decoded.topics().get(0).name());
            assertEquals(1, decoded.topics().get(0).partitions().size());
            assertEquals(0, decoded.topics().get(0).partitions().get(0).partitionIndex());
            assertEquals((short) 0, decoded.topics().get(0).partitions().get(0).errorCode());
            assertEquals(100L, decoded.topics().get(0).partitions().get(0).highWatermark());
            assertArrayEquals(new byte[]{1, 2}, decoded.topics().get(0).partitions().get(0).records());
            assertEquals("other", decoded.topics().get(1).name());
            assertEquals((short) -1, decoded.topics().get(1).partitions().get(0).errorCode());
            assertEquals(42L, decoded.topics().get(1).partitions().get(0).highWatermark());
            assertNull(decoded.topics().get(1).partitions().get(0).records(), "null Records round-trip");
            // ThrottleTimeMs is a v1+ field — the carried value (5) is not on the v0
            // wire and is decoded as 0.
            assertEquals(0, decoded.throttleTimeMs(), "ThrottleTimeMs (v1+) is absent from the v0 body");
        }

        @Test
        @DisplayName("v0 response has the exact 35-byte spec wire layout")
        void v0ExactBytes() {
            var resp = new FetchResponse(5, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L, new byte[]{1, 2})))));

            byte[] body = FetchCodec.encodeResponse((short) 0, resp);
            // 4 (topic count) + 2 (name len) + 5 (name) + 4 (partition count)
            // + 4 (partitionIndex) + 2 (errorCode) + 8 (highWatermark)
            // + 4 (records len) + 2 (records) = 35
            assertEquals(35, body.length, "exact v0 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(1, buf.getInt(), "Responses count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "PartitionIndex int32");
            assertEquals((short) 0, buf.getShort(), "ErrorCode int16");
            assertEquals(100L, buf.getLong(), "HighWatermark int64");
            assertEquals(2, buf.getInt(), "Records nullable-bytes length");
            byte[] records = new byte[2];
            buf.get(records);
            assertArrayEquals(new byte[]{1, 2}, records, "Records bytes");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v0 null Records round-trip via length -1")
        void v0NullRecords() {
            var resp = new FetchResponse(0, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 0L, null)))));

            byte[] body = FetchCodec.encodeResponse((short) 0, resp);
            ByteBuffer buf = ByteBuffer.wrap(body);
            buf.getInt(); // topic count
            buf.getShort(); // name length
            buf.position(buf.position() + 5); // name
            buf.getInt(); // partition count
            buf.getInt(); // partitionIndex
            buf.getShort(); // errorCode
            buf.getLong(); // highWatermark
            assertEquals(-1, buf.getInt(), "null Records encoded as length -1");

            assertNull(FetchCodec.decodeResponse((short) 0, ByteBuffer.wrap(body))
                    .topics().getFirst().partitions().getFirst().records());
        }
    }

    @Nested
    @DisplayName("Fetch response v1 (key 1) — + leading ThrottleTimeMs")
    class ResponseV1 {

        @Test
        @DisplayName("v1 response round-trips with ThrottleTimeMs")
        void v1RoundTrip() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L, new byte[]{1, 2}))),
                    new FetchResponse.TopicResponse("other", List.of(
                            new FetchResponse.PartitionResponse(1, (short) -1, 42L, null)))));

            byte[] body = FetchCodec.encodeResponse((short) 1, resp);
            var decoded = FetchCodec.decodeResponse((short) 1, ByteBuffer.wrap(body));

            assertEquals(42, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            assertEquals(2, decoded.topics().size(), "topic count");
            assertEquals(100L, decoded.topics().get(0).partitions().get(0).highWatermark());
            assertArrayEquals(new byte[]{1, 2}, decoded.topics().get(0).partitions().get(0).records());
            assertEquals((short) -1, decoded.topics().get(1).partitions().get(0).errorCode());
            assertNull(decoded.topics().get(1).partitions().get(0).records(), "null Records round-trip");
        }

        @Test
        @DisplayName("v1 response has the exact 39-byte spec wire layout")
        void v1ExactBytes() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L, new byte[]{1, 2})))));

            byte[] body = FetchCodec.encodeResponse((short) 1, resp);
            // 4 (throttleTimeMs) + 4 (topic count) + 2 (name len) + 5 (name)
            // + 4 (partition count) + 4 (partitionIndex) + 2 (errorCode)
            // + 8 (highWatermark) + 4 (records len) + 2 (records) = 39
            assertEquals(39, body.length, "exact v1 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(42, buf.getInt(), "ThrottleTimeMs int32 (leading)");
            assertEquals(1, buf.getInt(), "Responses count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "PartitionIndex int32");
            assertEquals((short) 0, buf.getShort(), "ErrorCode int16");
            assertEquals(100L, buf.getLong(), "HighWatermark int64");
            assertEquals(2, buf.getInt(), "Records nullable-bytes length");
            byte[] records = new byte[2];
            buf.get(records);
            assertArrayEquals(new byte[]{1, 2}, records, "Records bytes");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v1 response is the v0 layout + 4-byte leading ThrottleTimeMs")
        void v1IsV0PlusThrottle() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L, new byte[]{1, 2})))));

            byte[] v0 = FetchCodec.encodeResponse((short) 0, resp);
            byte[] v1 = FetchCodec.encodeResponse((short) 1, resp);

            assertEquals(v0.length + 4, v1.length, "v1 is exactly 4 bytes wider than v0");
            // The leading 4 bytes are ThrottleTimeMs; the tail must equal the v0 body.
            assertArrayEquals(v0, java.util.Arrays.copyOfRange(v1, 4, v1.length),
                    "tail of v1 equals the v0 body");
        }
    }

    @Nested
    @DisplayName("Fetch response v2 (key 1) — unchanged vs v1")
    class ResponseV2 {

        @Test
        @DisplayName("v2 response is byte-identical to v1 (spec: no field change)")
        void v2ByteIdenticalToV1() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L, new byte[]{1, 2}))),
                    new FetchResponse.TopicResponse("other", List.of(
                            new FetchResponse.PartitionResponse(1, (short) -1, 42L, null)))));

            byte[] v1 = FetchCodec.encodeResponse((short) 1, resp);
            byte[] v2 = FetchCodec.encodeResponse((short) 2, resp);

            assertArrayEquals(v1, v2, "v2 response must be byte-identical to v1");
        }

        @Test
        @DisplayName("v2 response round-trips through the v1 methods")
        void v2RoundTripThroughV1Methods() {
            var resp = new FetchResponse(7, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(3, (short) 5, 999L, null)))));

            byte[] body = FetchCodec.encodeResponse((short) 2, resp);
            var decoded = FetchCodec.decodeResponse((short) 2, ByteBuffer.wrap(body));

            assertEquals(7, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            assertEquals(1, decoded.topics().size(), "topic count");
            assertEquals(3, decoded.topics().get(0).partitions().get(0).partitionIndex());
            assertEquals((short) 5, decoded.topics().get(0).partitions().get(0).errorCode());
            assertEquals(999L, decoded.topics().get(0).partitions().get(0).highWatermark());
            assertNull(decoded.topics().get(0).partitions().get(0).records(), "null Records round-trip");
        }
    }

    @Nested
    @DisplayName("Fetch request v3 (key 1) — + MaxBytes after MinBytes")
    class RequestV3 {

        @Test
        @DisplayName("v3 request round-trips with MaxBytes on the wire")
        void v3RoundTrip() {
            var req = new FetchRequest(-1, 500, 1, 1048576,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536))),
                            new FetchRequest.TopicFetch("other", List.of())));

            byte[] body = FetchCodec.encodeRequest((short) 3, req);
            var decoded = FetchCodec.decodeRequest((short) 3, ByteBuffer.wrap(body));

            assertEquals(-1, decoded.replicaId(), "ReplicaId round-trip");
            assertEquals(500, decoded.maxWaitMs(), "MaxWaitMs round-trip");
            assertEquals(1, decoded.minBytes(), "MinBytes round-trip");
            assertEquals(1048576, decoded.maxBytes(), "MaxBytes (v3+) round-trip");
            assertEquals(2, decoded.topics().size(), "topic count");
            assertEquals("topic", decoded.topics().get(0).name());
            assertEquals(0, decoded.topics().get(0).partitions().get(0).partition());
            assertEquals(10L, decoded.topics().get(0).partitions().get(0).fetchOffset());
            assertEquals(65536, decoded.topics().get(0).partitions().get(0).partitionMaxBytes());
            assertEquals("other", decoded.topics().get(1).name());
            assertEquals(0, decoded.topics().get(1).partitions().size(), "empty partition list");
        }

        @Test
        @DisplayName("v3 request has the exact 47-byte spec wire layout (v0 + 4-byte MaxBytes)")
        void v3ExactBytes() {
            var req = new FetchRequest(-1, 500, 1, 1048576,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));

            byte[] body = FetchCodec.encodeRequest((short) 3, req);
            // 4 (replicaId) + 4 (maxWaitMs) + 4 (minBytes) + 4 (maxBytes, v3+)
            // + 4 (topic count) + 2 (name len) + 5 (name) + 4 (partition count)
            // + 4 (partition) + 8 (fetchOffset) + 4 (partitionMaxBytes) = 47
            assertEquals(47, body.length, "exact v3 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(-1, buf.getInt(), "ReplicaId int32");
            assertEquals(500, buf.getInt(), "MaxWaitMs int32");
            assertEquals(1, buf.getInt(), "MinBytes int32");
            assertEquals(1048576, buf.getInt(), "MaxBytes int32 (v3+)");
            assertEquals(1, buf.getInt(), "Topics count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "Partition int32");
            assertEquals(10L, buf.getLong(), "FetchOffset int64");
            assertEquals(65536, buf.getInt(), "PartitionMaxBytes int32");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v3 request is the v0 layout with MaxBytes inserted after MinBytes")
        void v3IsV0PlusMaxBytes() {
            var req = new FetchRequest(-1, 500, 1, 1048576,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));

            byte[] v0 = FetchCodec.encodeRequest((short) 0, req);
            byte[] v3 = FetchCodec.encodeRequest((short) 3, req);

            assertEquals(v0.length + 4, v3.length, "v3 is 4 bytes wider than v0");
            ByteBuffer maxBytes = ByteBuffer.allocate(4).putInt(1048576);
            // v3 = v0[0..12) + MaxBytes + v0[12..)
            assertArrayEquals(Arrays.copyOfRange(v0, 0, 12), Arrays.copyOfRange(v3, 0, 12),
                    "ReplicaId/MaxWaitMs/MinBytes unchanged");
            assertArrayEquals(maxBytes.array(), Arrays.copyOfRange(v3, 12, 16),
                    "MaxBytes sits between MinBytes and the topic array");
            assertArrayEquals(Arrays.copyOfRange(v0, 12, v0.length),
                    Arrays.copyOfRange(v3, 16, v3.length),
                    "topic array byte-identical to v0");
        }
    }

    @Nested
    @DisplayName("Fetch response v3 (key 1) — unchanged vs v1/v2")
    class ResponseV3 {

        @Test
        @DisplayName("v3 response is byte-identical to v1 (spec: v3 changes the request only)")
        void v3ByteIdenticalToV1() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L, new byte[]{1, 2}))),
                    new FetchResponse.TopicResponse("other", List.of(
                            new FetchResponse.PartitionResponse(1, (short) -1, 42L, null)))));

            byte[] v1 = FetchCodec.encodeResponse((short) 1, resp);
            byte[] v3 = FetchCodec.encodeResponse((short) 3, resp);

            assertArrayEquals(v1, v3, "v3 response must be byte-identical to v1");
        }

        @Test
        @DisplayName("v3 response round-trips through the v1 methods")
        void v3RoundTripThroughV1Methods() {
            var resp = new FetchResponse(7, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(3, (short) 5, 999L, null)))));

            byte[] body = FetchCodec.encodeResponse((short) 3, resp);
            var decoded = FetchCodec.decodeResponse((short) 3, ByteBuffer.wrap(body));

            assertEquals(7, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            assertEquals(1, decoded.topics().size(), "topic count");
            assertEquals(3, decoded.topics().get(0).partitions().get(0).partitionIndex());
            assertEquals((short) 5, decoded.topics().get(0).partitions().get(0).errorCode());
            assertEquals(999L, decoded.topics().get(0).partitions().get(0).highWatermark());
            assertNull(decoded.topics().get(0).partitions().get(0).records(), "null Records round-trip");
        }
    }

    @Nested
    @DisplayName("Fetch request v4 (key 1) — + IsolationLevel after MaxBytes")
    class RequestV4 {

        @Test
        @DisplayName("v4 request round-trips with IsolationLevel on the wire")
        void v4RoundTrip() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 1,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536))),
                            new FetchRequest.TopicFetch("other", List.of())));

            byte[] body = FetchCodec.encodeRequest((short) 4, req);
            var decoded = FetchCodec.decodeRequest((short) 4, ByteBuffer.wrap(body));

            assertEquals(-1, decoded.replicaId(), "ReplicaId round-trip");
            assertEquals(500, decoded.maxWaitMs(), "MaxWaitMs round-trip");
            assertEquals(1, decoded.minBytes(), "MinBytes round-trip");
            assertEquals(1048576, decoded.maxBytes(), "MaxBytes round-trip");
            assertEquals(1, decoded.isolationLevel(), "IsolationLevel (v4+) round-trip");
            assertEquals(2, decoded.topics().size(), "topic count");
            assertEquals("topic", decoded.topics().get(0).name());
            assertEquals(0, decoded.topics().get(0).partitions().get(0).partition());
            assertEquals(10L, decoded.topics().get(0).partitions().get(0).fetchOffset());
            assertEquals(65536, decoded.topics().get(0).partitions().get(0).partitionMaxBytes());
            assertEquals("other", decoded.topics().get(1).name());
        }

        @Test
        @DisplayName("v4 request has the exact 48-byte spec wire layout (v3 + 1-byte IsolationLevel)")
        void v4ExactBytes() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 0,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));

            byte[] body = FetchCodec.encodeRequest((short) 4, req);
            // 4 (replicaId) + 4 (maxWaitMs) + 4 (minBytes) + 4 (maxBytes)
            // + 1 (isolationLevel, v4+) + 4 (topic count) + 2 (name len) + 5 (name)
            // + 4 (partition count) + 4 (partition) + 8 (fetchOffset) + 4 (partitionMaxBytes) = 48
            assertEquals(48, body.length, "exact v4 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(-1, buf.getInt(), "ReplicaId int32");
            assertEquals(500, buf.getInt(), "MaxWaitMs int32");
            assertEquals(1, buf.getInt(), "MinBytes int32");
            assertEquals(1048576, buf.getInt(), "MaxBytes int32");
            assertEquals(0, buf.get() & 0xff, "IsolationLevel int8 (v4+)");
            assertEquals(1, buf.getInt(), "Topics count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "Partition int32");
            assertEquals(10L, buf.getLong(), "FetchOffset int64");
            assertEquals(65536, buf.getInt(), "PartitionMaxBytes int32");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v4 request is the v3 layout with IsolationLevel inserted after MaxBytes")
        void v4IsV3PlusIsolationLevel() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 1,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));

            byte[] v3 = FetchCodec.encodeRequest((short) 3, req);
            byte[] v4 = FetchCodec.encodeRequest((short) 4, req);

            assertEquals(v3.length + 1, v4.length, "v4 is 1 byte wider than v3");
            assertArrayEquals(Arrays.copyOfRange(v3, 0, 16), Arrays.copyOfRange(v4, 0, 16),
                    "ReplicaId/MaxWaitMs/MinBytes/MaxBytes (16 bytes) unchanged");
            assertEquals(1, v4[16], "IsolationLevel int8 sits between MaxBytes and the topic array");
            assertArrayEquals(Arrays.copyOfRange(v3, 16, v3.length),
                    Arrays.copyOfRange(v4, 17, v4.length),
                    "topic array byte-identical to v3");
        }

        @Test
        @DisplayName("v0-v3 decode defaults isolationLevel to 0 (absent from the body)")
        void preV4DefaultsIsolationLevel() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 1,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));

            for (short v = 0; v <= 3; v++) {
                byte[] body = FetchCodec.encodeRequest(v, req);
                var decoded = FetchCodec.decodeRequest(v, ByteBuffer.wrap(body));
                assertEquals(0, decoded.isolationLevel(),
                        "IsolationLevel (v4+) must decode as 0 at v" + v);
            }
        }
    }

    @Nested
    @DisplayName("Fetch response v4 (key 1) — + LastStableOffset + AbortedTransactions")
    class ResponseV4 {

        @Test
        @DisplayName("v4 response round-trips with LastStableOffset + AbortedTransactions")
        void v4RoundTrip() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L,
                                    99L,
                                    List.of(new FetchResponse.AbortedTransaction(7L, 12L),
                                            new FetchResponse.AbortedTransaction(8L, 30L)),
                                    new byte[]{1, 2}))),
                    new FetchResponse.TopicResponse("other", List.of(
                            new FetchResponse.PartitionResponse(1, (short) -1, 42L, -1L,
                                    null, null)))));

            byte[] body = FetchCodec.encodeResponse((short) 4, resp);
            var decoded = FetchCodec.decodeResponse((short) 4, ByteBuffer.wrap(body));

            assertEquals(42, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            assertEquals(2, decoded.topics().size(), "topic count");
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(0, p0.partitionIndex());
            assertEquals((short) 0, p0.errorCode());
            assertEquals(100L, p0.highWatermark());
            assertEquals(99L, p0.lastStableOffset(), "LastStableOffset (v4+) round-trip");
            assertEquals(2, p0.abortedTransactions().size(), "AbortedTransactions count (v4+)");
            assertEquals(7L, p0.abortedTransactions().get(0).producerId());
            assertEquals(12L, p0.abortedTransactions().get(0).firstOffset());
            assertEquals(8L, p0.abortedTransactions().get(1).producerId());
            assertEquals(30L, p0.abortedTransactions().get(1).firstOffset());
            assertArrayEquals(new byte[]{1, 2}, p0.records(), "Records round-trip");
            var p1 = decoded.topics().get(1).partitions().get(0);
            assertEquals(-1L, p1.lastStableOffset(), "absent LastStableOffset defaults -1");
            assertTrue(p1.abortedTransactions().isEmpty(), "null AbortedTransactions decodes as empty list");
            assertNull(p1.records(), "null Records round-trip");
        }

        @Test
        @DisplayName("v4 response has the exact per-field wire layout (v1 + 8 LastStableOffset + 4 aborted count per partition)")
        void v4ExactBytes() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L,
                                    99L,
                                    List.of(new FetchResponse.AbortedTransaction(7L, 12L)),
                                    new byte[]{1, 2})))));

            byte[] body = FetchCodec.encodeResponse((short) 4, resp);
            // 4 (throttleTimeMs) + 4 (topic count) + 2 (name len) + 5 (name) + 4 (partition count)
            // + 4 (partitionIndex) + 2 (errorCode) + 8 (highWatermark) + 8 (lastStableOffset, v4+)
            // + 4 (aborted count, v4+) + 8 (producerId) + 8 (firstOffset)
            // + 4 (records len) + 2 (records) = 67
            assertEquals(67, body.length, "exact v4 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(42, buf.getInt(), "ThrottleTimeMs int32");
            assertEquals(1, buf.getInt(), "Responses count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "PartitionIndex int32");
            assertEquals(0, buf.getShort(), "ErrorCode int16");
            assertEquals(100L, buf.getLong(), "HighWatermark int64");
            assertEquals(99L, buf.getLong(), "LastStableOffset int64 (v4+)");
            assertEquals(1, buf.getInt(), "AbortedTransactions count int32 (v4+)");
            assertEquals(7L, buf.getLong(), "ProducerId int64");
            assertEquals(12L, buf.getLong(), "FirstOffset int64");
            assertEquals(2, buf.getInt(), "Records length int32");
            byte[] recs = new byte[2];
            buf.get(recs);
            assertArrayEquals(new byte[]{1, 2}, recs, "Records bytes");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v4 null AbortedTransactions encodes as an empty array (count 0)")
        void v4NullAbortedEncodesAsEmpty() {
            var resp = new FetchResponse(0, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 10L, -1L, null, null)))));

            byte[] body = FetchCodec.encodeResponse((short) 4, resp);
            // ThrottleTimeMs(4) + topic count(4) + 2 (name len) + 5 (name) + partition count(4)
            // + partitionIndex(4) + errorCode(2) + highWatermark(8) + lastStableOffset(8)
            // + aborted count(4) + records len(4) = 49
            assertEquals(49, body.length, "exact v4 wire layout with empty aborted array");
            ByteBuffer buf = ByteBuffer.wrap(body);
            buf.position(4 + 4 + 2 + 5 + 4 + 4 + 2 + 8 + 8);
            assertEquals(0, buf.getInt(), "AbortedTransactions count is 0 for null");
            var decoded = FetchCodec.decodeResponse((short) 4, ByteBuffer.wrap(body));
            assertTrue(decoded.topics().get(0).partitions().get(0).abortedTransactions().isEmpty());
        }
    }

    @Nested
    @DisplayName("Fetch request v5 (key 1) — per-partition + LogStartOffset after FetchOffset")
    class RequestV5 {

        @Test
        @DisplayName("v5 request round-trips with LogStartOffset on the wire")
        void v5RoundTrip() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 0,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, 42L)))));

            byte[] body = FetchCodec.encodeRequest((short) 5, req);
            var decoded = FetchCodec.decodeRequest((short) 5, ByteBuffer.wrap(body));

            assertEquals(-1, decoded.replicaId());
            assertEquals(500, decoded.maxWaitMs());
            assertEquals(1, decoded.minBytes());
            assertEquals(1048576, decoded.maxBytes());
            assertEquals(0, decoded.isolationLevel());
            assertEquals(42L, decoded.topics().get(0).partitions().get(0).logStartOffset(),
                    "LogStartOffset (v5+) round-trip");
        }

        @Test
        @DisplayName("v5 request has the exact 56-byte spec wire layout (v4 + 8-byte LogStartOffset)")
        void v5ExactBytes() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 0,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, 42L)))));

            byte[] body = FetchCodec.encodeRequest((short) 5, req);
            // 4 (replicaId) + 4 (maxWaitMs) + 4 (minBytes) + 4 (maxBytes)
            // + 1 (isolationLevel) + 4 (topic count) + 2 (name len) + 5 (name)
            // + 4 (partition count) + 4 (partition) + 8 (fetchOffset)
            // + 8 (logStartOffset, v5+) + 4 (partitionMaxBytes) = 56
            assertEquals(56, body.length, "exact v5 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(-1, buf.getInt(), "ReplicaId int32");
            assertEquals(500, buf.getInt(), "MaxWaitMs int32");
            assertEquals(1, buf.getInt(), "MinBytes int32");
            assertEquals(1048576, buf.getInt(), "MaxBytes int32");
            assertEquals(0, buf.get() & 0xff, "IsolationLevel int8 (v4+)");
            assertEquals(1, buf.getInt(), "Topics count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "Partition int32");
            assertEquals(10L, buf.getLong(), "FetchOffset int64");
            assertEquals(42L, buf.getLong(), "LogStartOffset int64 (v5+, after FetchOffset)");
            assertEquals(65536, buf.getInt(), "PartitionMaxBytes int32");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v5 request is the v4 layout with 8-byte LogStartOffset inserted after FetchOffset per partition")
        void v5IsV4PlusLogStartOffset() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 1,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, 42L)))));

            byte[] v4 = FetchCodec.encodeRequest((short) 4, req);
            byte[] v5 = FetchCodec.encodeRequest((short) 5, req);

            assertEquals(v4.length + 8, v5.length, "v5 is 8 bytes wider per partition");
            // Prefix through FetchOffset: 4+4+4+4+1+4+4+2+5+4+4+8 = 44 bytes
            assertArrayEquals(Arrays.copyOfRange(v4, 0, 44), Arrays.copyOfRange(v5, 0, 44),
                    "header + partition prefix (through FetchOffset) unchanged");
            assertEquals(42L, ByteBuffer.wrap(v5).position(44).getLong(),
                    "LogStartOffset int64 sits between FetchOffset and PartitionMaxBytes");
            assertArrayEquals(Arrays.copyOfRange(v4, 44, v4.length),
                    Arrays.copyOfRange(v5, 52, v5.length),
                    "PartitionMaxBytes byte-identical to v4");
        }

        @Test
        @DisplayName("v0-v4 decode defaults logStartOffset to -1 (absent from the body)")
        void preV5DefaultsLogStartOffset() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 0,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));

            for (short v = 0; v <= 4; v++) {
                byte[] body = FetchCodec.encodeRequest(v, req);
                var decoded = FetchCodec.decodeRequest(v, ByteBuffer.wrap(body));
                assertEquals(-1L, decoded.topics().get(0).partitions().get(0).logStartOffset(),
                        "LogStartOffset (v5+) must decode as -1 at v" + v);
            }
        }
    }

    @Nested
    @DisplayName("Fetch response v5 (key 1) — per-partition + LogStartOffset after LastStableOffset")
    class ResponseV5 {

        @Test
        @DisplayName("v5 response round-trips with LogStartOffset")
        void v5RoundTrip() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(0).errorCode((short) 0).highWatermark(100L).lastStableOffset(99L).logStartOffset(7L).abortedTransactions(List.of(new FetchResponse.AbortedTransaction(7L, 12L))).records(new byte[]{1, 2}).build()))));

            byte[] body = FetchCodec.encodeResponse((short) 5, resp);
            var decoded = FetchCodec.decodeResponse((short) 5, ByteBuffer.wrap(body));

            assertEquals(42, decoded.throttleTimeMs());
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(99L, p0.lastStableOffset());
            assertEquals(7L, p0.logStartOffset(), "LogStartOffset (v5+) round-trip");
            assertEquals(1, p0.abortedTransactions().size());
            assertEquals(7L, p0.abortedTransactions().get(0).producerId());
            assertArrayEquals(new byte[]{1, 2}, p0.records());
        }

        @Test
        @DisplayName("v5 response has the exact 75-byte spec wire layout (v4 + 8-byte LogStartOffset)")
        void v5ExactBytes() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(0).errorCode((short) 0).highWatermark(100L).lastStableOffset(99L).logStartOffset(7L).abortedTransactions(List.of(new FetchResponse.AbortedTransaction(7L, 12L))).records(new byte[]{1, 2}).build()))));

            byte[] body = FetchCodec.encodeResponse((short) 5, resp);
            // 4 (throttleTimeMs) + 4 (topic count) + 2 (name len) + 5 (name) + 4 (partition count)
            // + 4 (partitionIndex) + 2 (errorCode) + 8 (highWatermark)
            // + 8 (lastStableOffset, v4+) + 8 (logStartOffset, v5+)
            // + 4 (aborted count, v4+) + 8 (producerId) + 8 (firstOffset)
            // + 4 (records len) + 2 (records) = 75
            assertEquals(75, body.length, "exact v5 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(42, buf.getInt(), "ThrottleTimeMs int32");
            assertEquals(1, buf.getInt(), "Responses count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "PartitionIndex int32");
            assertEquals(0, buf.getShort(), "ErrorCode int16");
            assertEquals(100L, buf.getLong(), "HighWatermark int64");
            assertEquals(99L, buf.getLong(), "LastStableOffset int64 (v4+)");
            assertEquals(7L, buf.getLong(), "LogStartOffset int64 (v5+, after LastStableOffset)");
            assertEquals(1, buf.getInt(), "AbortedTransactions count int32 (v4+)");
            assertEquals(7L, buf.getLong(), "ProducerId int64");
            assertEquals(12L, buf.getLong(), "FirstOffset int64");
            assertEquals(2, buf.getInt(), "Records length int32");
            byte[] recs = new byte[2];
            buf.get(recs);
            assertArrayEquals(new byte[]{1, 2}, recs, "Records bytes");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v5 null AbortedTransactions encodes as an empty array (count 0)")
        void v5NullAbortedEncodesAsEmpty() {
            var resp = new FetchResponse(0, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(0).errorCode((short) 0).highWatermark(10L).lastStableOffset(99L).logStartOffset(7L).abortedTransactions(null).records(null).build()))));

            byte[] body = FetchCodec.encodeResponse((short) 5, resp);
            // ThrottleTimeMs(4) + topic count(4) + 2 (name len) + 5 (name) + partition count(4)
            // + partitionIndex(4) + errorCode(2) + highWatermark(8) + lastStableOffset(8)
            // + logStartOffset(8) + aborted count(4) + records len(4) = 57
            assertEquals(57, body.length, "exact v5 wire layout with empty aborted array");
            ByteBuffer buf = ByteBuffer.wrap(body);
            buf.position(4 + 4 + 2 + 5 + 4 + 4 + 2 + 8 + 8 + 8);
            assertEquals(0, buf.getInt(), "AbortedTransactions count is 0 for null");
            var decoded = FetchCodec.decodeResponse((short) 5, ByteBuffer.wrap(body));
            assertTrue(decoded.topics().get(0).partitions().get(0).abortedTransactions().isEmpty());
        }
    }

    @Nested
    @DisplayName("Fetch request v6 (key 1) — unchanged vs v5")
    class RequestV6 {

        @Test
        @DisplayName("v6 request is byte-identical to v5 (spec: no field change)")
        void v6ByteIdenticalToV5() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 1,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, 42L))),
                            new FetchRequest.TopicFetch("other", List.of())));

            byte[] v5 = FetchCodec.encodeRequest((short) 5, req);
            byte[] v6 = FetchCodec.encodeRequest((short) 6, req);

            assertArrayEquals(v5, v6, "v6 request must be byte-identical to v5");
        }

        @Test
        @DisplayName("v6 request round-trips through the v5 methods")
        void v6RoundTripThroughV5Methods() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 0,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, -1L)))));

            byte[] body = FetchCodec.encodeRequest((short) 6, req);
            var decoded = FetchCodec.decodeRequest((short) 6, ByteBuffer.wrap(body));

            assertEquals(-1, decoded.replicaId(), "ReplicaId round-trip");
            assertEquals(500, decoded.maxWaitMs(), "MaxWaitMs round-trip");
            assertEquals(1, decoded.minBytes(), "MinBytes round-trip");
            assertEquals(1048576, decoded.maxBytes(), "MaxBytes round-trip");
            assertEquals(0, decoded.isolationLevel(), "IsolationLevel round-trip");
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(0, p0.partition(), "Partition round-trip");
            assertEquals(10L, p0.fetchOffset(), "FetchOffset round-trip");
            assertEquals(-1L, p0.logStartOffset(), "LogStartOffset round-trip");
            assertEquals(65536, p0.partitionMaxBytes(), "PartitionMaxBytes round-trip");
        }
    }

    @Nested
    @DisplayName("Fetch response v6 (key 1) — unchanged vs v5")
    class ResponseV6 {

        @Test
        @DisplayName("v6 response is byte-identical to v5 (spec: no field change)")
        void v6ByteIdenticalToV5() {
            var resp = new FetchResponse(42, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(0).errorCode((short) 0).highWatermark(100L).lastStableOffset(99L).logStartOffset(7L).abortedTransactions(List.of(new FetchResponse.AbortedTransaction(7L, 12L))).records(new byte[]{1, 2}).build())),
                    new FetchResponse.TopicResponse("other", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(1).errorCode((short) -1).highWatermark(42L).lastStableOffset(99L).logStartOffset(7L).abortedTransactions(null).records(null).build()))));

            byte[] v5 = FetchCodec.encodeResponse((short) 5, resp);
            byte[] v6 = FetchCodec.encodeResponse((short) 6, resp);

            assertArrayEquals(v5, v6, "v6 response must be byte-identical to v5");
        }

        @Test
        @DisplayName("v6 response round-trips through the v5 methods")
        void v6RoundTripThroughV5Methods() {
            var resp = new FetchResponse(7, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(3).errorCode((short) 5).highWatermark(999L).lastStableOffset(988L).logStartOffset(12L).abortedTransactions(List.of(new FetchResponse.AbortedTransaction(1L, 2L))).records(null).build()))));

            byte[] body = FetchCodec.encodeResponse((short) 6, resp);
            var decoded = FetchCodec.decodeResponse((short) 6, ByteBuffer.wrap(body));

            assertEquals(7, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(3, p0.partitionIndex(), "PartitionIndex round-trip");
            assertEquals((short) 5, p0.errorCode(), "ErrorCode round-trip");
            assertEquals(999L, p0.highWatermark(), "HighWatermark round-trip");
            assertEquals(988L, p0.lastStableOffset(), "LastStableOffset round-trip");
            assertEquals(12L, p0.logStartOffset(), "LogStartOffset round-trip");
            assertEquals(1, p0.abortedTransactions().size(), "AbortedTransactions round-trip");
            assertNull(p0.records(), "null Records round-trip");
        }
    }

    @Nested
    @DisplayName("Fetch request v7 (key 1) — + SessionId/SessionEpoch + ForgottenTopicsData")
    class RequestV7 {

        @Test
        @DisplayName("v7 request round-trips with SessionId/SessionEpoch + ForgottenTopicsData")
        void v7RoundTrip() {
            var req = FetchRequest.builder().replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(1).sessionId(11).sessionEpoch(3).topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, 42L))))).forgottenTopics(List.of(new FetchRequest.ForgottenTopic("topic", List.of(1, 2)),
                            new FetchRequest.ForgottenTopic("other", List.of()))).build();

            byte[] body = FetchCodec.encodeRequest((short) 7, req);
            var decoded = FetchCodec.decodeRequest((short) 7, ByteBuffer.wrap(body));

            assertEquals(-1, decoded.replicaId());
            assertEquals(500, decoded.maxWaitMs());
            assertEquals(1, decoded.minBytes());
            assertEquals(1048576, decoded.maxBytes());
            assertEquals(1, decoded.isolationLevel());
            assertEquals(11L, decoded.sessionId(), "SessionId (v7+) round-trip");
            assertEquals(3L, decoded.sessionEpoch(), "SessionEpoch (v7+) round-trip");
            assertEquals(42L, decoded.topics().get(0).partitions().get(0).logStartOffset());
            assertEquals(2, decoded.forgottenTopics().size(), "ForgottenTopicsData count");
            assertEquals("topic", decoded.forgottenTopics().get(0).name());
            assertEquals(List.of(1, 2), decoded.forgottenTopics().get(0).partitions());
            assertEquals("other", decoded.forgottenTopics().get(1).name());
            assertTrue(decoded.forgottenTopics().get(1).partitions().isEmpty(),
                    "empty partition list = whole topic forgotten");
        }

        @Test
        @DisplayName("v7 request has the exact 87-byte spec wire layout (v5 + 8 session + 8 + 23 forgotten)")
        void v7ExactBytes() {
            var req = FetchRequest.builder().replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(0).sessionId(11).sessionEpoch(3).topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, 42L))))).forgottenTopics(List.of(new FetchRequest.ForgottenTopic("topic", List.of(1, 2)))).build();

            byte[] body = FetchCodec.encodeRequest((short) 7, req);
            // v5 layout for this shape is 56 bytes (header 17 + topics count 4 + topic 7
            // + partition count 4 + partition 24). v7 adds 8 (SessionId+SessionEpoch) after
            // IsolationLevel and a trailing ForgottenTopicsData: 4 (array count) + 2 (name len)
            // + 5 (name) + 4 (part count) + 8 (two part ints) = 23 after Topics. 56 + 8 + 23 = 87.
            assertEquals(87, body.length, "exact v7 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(-1, buf.getInt(), "ReplicaId int32");
            assertEquals(500, buf.getInt(), "MaxWaitMs int32");
            assertEquals(1, buf.getInt(), "MinBytes int32");
            assertEquals(1048576, buf.getInt(), "MaxBytes int32");
            assertEquals(0, buf.get() & 0xff, "IsolationLevel int8 (v4+)");
            assertEquals(11L, buf.getInt(), "SessionId int32 (v7+, after IsolationLevel)");
            assertEquals(3L, buf.getInt(), "SessionEpoch int32 (v7+)");
            assertEquals(1, buf.getInt(), "Topics count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "Partition int32");
            assertEquals(10L, buf.getLong(), "FetchOffset int64");
            assertEquals(42L, buf.getLong(), "LogStartOffset int64 (v5+)");
            assertEquals(65536, buf.getInt(), "PartitionMaxBytes int32");
            assertEquals(1, buf.getInt(), "ForgottenTopicsData count int32 (v7+)");
            assertEquals(5, buf.getShort(), "Forgotten topic string16 length");
            byte[] fname = new byte[5];
            buf.get(fname);
            assertEquals("topic", new String(fname, StandardCharsets.UTF_8), "Forgotten topic name");
            assertEquals(2, buf.getInt(), "Forgotten partitions count int32");
            assertEquals(1, buf.getInt(), "Forgotten partition[0] int32");
            assertEquals(2, buf.getInt(), "Forgotten partition[1] int32");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v7 request is the v5 layout with session fields after IsolationLevel + ForgottenTopicsData trailing")
        void v7IsV5PlusSession() {
            var req = FetchRequest.builder().replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(1).sessionId(11).sessionEpoch(3).topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, 42L))))).forgottenTopics(List.of(new FetchRequest.ForgottenTopic("topic", List.of(1, 2)))).build();

            byte[] v5 = FetchCodec.encodeRequest((short) 5, req);
            byte[] v7 = FetchCodec.encodeRequest((short) 7, req);

            // v5 is 56 bytes. v7 header prefix through IsolationLevel is 17 bytes
            // (ReplicaId 4 + MaxWaitMs 4 + MinBytes 4 + MaxBytes 4 + IsolationLevel 1).
            // v7 then inserts 8 (session) bytes, so session sits at [17..25) and Topics
            // begins at 25 — in v5 Topics began at 17. The 17-byte header prefix is unchanged.
            assertArrayEquals(Arrays.copyOfRange(v5, 0, 17), Arrays.copyOfRange(v7, 0, 17),
                    "header through IsolationLevel unchanged");
            // v5: Topics at offset 17 (length 56-17 = 39). v7: Topics at offset 25.
            // v7 = header(17) + session(8) + topics(39) + forgotten(23) = 87.
            assertEquals(v5.length + 8 + 23, v7.length, "v7 = v5 + 8 session + 23 forgotten");
            assertArrayEquals(Arrays.copyOfRange(v5, 17, v5.length),
                    Arrays.copyOfRange(v7, 25, 25 + (v5.length - 17)),
                    "Topics array byte-identical between v5 and v7");
        }

        @Test
        @DisplayName("v0-v6 decode defaults sessionId=0, sessionEpoch=-1, forgotten=empty (absent from the body)")
        void preV7DefaultsSession() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 0,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));
            for (short v = 0; v <= 6; v++) {
                byte[] body = FetchCodec.encodeRequest(v, req);
                var decoded = FetchCodec.decodeRequest(v, ByteBuffer.wrap(body));
                assertEquals(0, decoded.sessionId(), "SessionId (v7+) must decode as 0 at v" + v);
                assertEquals(-1, decoded.sessionEpoch(), "SessionEpoch (v7+) must decode as -1 at v" + v);
                assertTrue(decoded.forgottenTopics().isEmpty(),
                        "ForgottenTopicsData (v7+) must decode as empty at v" + v);
            }
        }
    }

    @Nested
    @DisplayName("Fetch response v7 (key 1) — + top-level ErrorCode + SessionId")
    class ResponseV7 {

        @Test
        @DisplayName("v7 response round-trips with top-level ErrorCode + SessionId")
        void v7RoundTrip() {
            var resp = FetchResponse.builder().throttleTimeMs(7).errorCode((short) 3).sessionId(11).topics(List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(3).errorCode((short) 5).highWatermark(999L).lastStableOffset(988L).logStartOffset(12L).abortedTransactions(List.of(new FetchResponse.AbortedTransaction(1L, 2L))).records(null).build())))).build();

            byte[] body = FetchCodec.encodeResponse((short) 7, resp);
            var decoded = FetchCodec.decodeResponse((short) 7, ByteBuffer.wrap(body));

            assertEquals(7, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            assertEquals((short) 3, decoded.errorCode(), "top-level ErrorCode (v7+) round-trip");
            assertEquals(11L, decoded.sessionId(), "top-level SessionId (v7+) round-trip");
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(3, p0.partitionIndex(), "PartitionIndex round-trip");
            assertEquals((short) 5, p0.errorCode(), "partition ErrorCode round-trip");
            assertEquals(999L, p0.highWatermark(), "HighWatermark round-trip");
            assertEquals(988L, p0.lastStableOffset(), "LastStableOffset round-trip");
            assertEquals(12L, p0.logStartOffset(), "LogStartOffset round-trip");
        }

        @Test
        @DisplayName("v7 response has the exact 63-byte spec wire layout (v5 + 2 ErrorCode + 4 SessionId)")
        void v7ExactBytes() {
            var resp = FetchResponse.builder().throttleTimeMs(7).errorCode((short) 3).sessionId(11).topics(List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(3).errorCode((short) 5).highWatermark(999L).lastStableOffset(988L).logStartOffset(12L).abortedTransactions(null).records(null).build())))).build();

            byte[] body = FetchCodec.encodeResponse((short) 7, resp);
            // v5 response for this shape is 57 bytes (ThrottleTimeMs 4 + responses count 4
            // + topic 7 + partition count 4 + partition 38). v7 adds 6 bytes (ErrorCode int16
            // + SessionId int32) between ThrottleTimeMs and Responses. 57 + 6 = 63.
            assertEquals(63, body.length, "exact v7 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(7, buf.getInt(), "ThrottleTimeMs int32");
            assertEquals((short) 3, buf.getShort(), "ErrorCode int16 (v7+, after ThrottleTimeMs)");
            assertEquals(11L, buf.getInt(), "SessionId int32 (v7+)");
            assertEquals(1, buf.getInt(), "Responses count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(3, buf.getInt(), "PartitionIndex int32");
            assertEquals((short) 5, buf.getShort(), "Partition ErrorCode int16");
            assertEquals(999L, buf.getLong(), "HighWatermark int64");
            assertEquals(988L, buf.getLong(), "LastStableOffset int64 (v4+)");
            assertEquals(12L, buf.getLong(), "LogStartOffset int64 (v5+)");
            assertEquals(0, buf.getInt(), "AbortedTransactions count int32 (v4+, empty)");
            assertEquals(-1, buf.getInt(), "Records length int32 (null)");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v7 response is the v5 layout with top-level ErrorCode+SessionId inserted after ThrottleTimeMs")
        void v7IsV5PlusTopLevel() {
            var resp = FetchResponse.builder().throttleTimeMs(7).errorCode((short) 3).sessionId(11).topics(List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(3).errorCode((short) 5).highWatermark(999L).lastStableOffset(988L).logStartOffset(12L).abortedTransactions(null).records(null).build())))).build();

            byte[] v5 = FetchCodec.encodeResponse((short) 5, resp);
            byte[] v7 = FetchCodec.encodeResponse((short) 7, resp);

            // v5 is 75 bytes; v7 = 75 + 6 (top-level ErrorCode+SessionId).
            assertEquals(v5.length + 6, v7.length, "v7 is 6 bytes wider");
            // ThrottleTimeMs (first 4 bytes) unchanged.
            assertArrayEquals(Arrays.copyOfRange(v5, 0, 4), Arrays.copyOfRange(v7, 0, 4),
                    "ThrottleTimeMs unchanged");
            // The v5 Responses array (from offset 4) equals the v7 Responses array (from offset 10).
            assertArrayEquals(Arrays.copyOfRange(v5, 4, v5.length),
                    Arrays.copyOfRange(v7, 10, v7.length),
                    "Responses array byte-identical between v5 and v7");
        }

        @Test
        @DisplayName("v0-v6 decode defaults errorCode=0, sessionId=0 (absent from the body)")
        void preV7DefaultsTopLevel() {
            var resp = new FetchResponse(7, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L, null)))));
            for (short v = 0; v <= 6; v++) {
                byte[] body = FetchCodec.encodeResponse(v, resp);
                var decoded = FetchCodec.decodeResponse(v, ByteBuffer.wrap(body));
                assertEquals(0, decoded.errorCode(), "ErrorCode (v7+) must decode as 0 at v" + v);
                assertEquals(0, decoded.sessionId(), "SessionId (v7+) must decode as 0 at v" + v);
            }
        }
    }

    @Nested
    @DisplayName("Fetch request v8 (key 1) — unchanged vs v7")
    class RequestV8 {

        @Test
        @DisplayName("v8 request is byte-identical to v7 (spec: no field change)")
        void v8ByteIdenticalToV7() {
            var req = FetchRequest.builder().replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(1).sessionId(11).sessionEpoch(3).topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, 42L))))).forgottenTopics(List.of(new FetchRequest.ForgottenTopic("topic", List.of(1, 2)))).build();

            byte[] v7 = FetchCodec.encodeRequest((short) 7, req);
            byte[] v8 = FetchCodec.encodeRequest((short) 8, req);

            assertArrayEquals(v7, v8, "v8 request must be byte-identical to v7");
        }

        @Test
        @DisplayName("v8 request round-trips through the v7 methods")
        void v8RoundTripThroughV7Methods() {
            var req = FetchRequest.builder().replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(0).sessionId(11).sessionEpoch(3).topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536, -1L))))).forgottenTopics(List.of(new FetchRequest.ForgottenTopic("other", List.of()))).build();

            byte[] body = FetchCodec.encodeRequest((short) 8, req);
            var decoded = FetchCodec.decodeRequest((short) 8, ByteBuffer.wrap(body));

            assertEquals(11L, decoded.sessionId(), "SessionId round-trip");
            assertEquals(3L, decoded.sessionEpoch(), "SessionEpoch round-trip");
            assertEquals(1, decoded.forgottenTopics().size(), "ForgottenTopicsData round-trip");
            assertEquals("other", decoded.forgottenTopics().get(0).name());
        }
    }

    @Nested
    @DisplayName("Fetch response v8 (key 1) — unchanged vs v7")
    class ResponseV8 {

        @Test
        @DisplayName("v8 response is byte-identical to v7 (spec: no field change)")
        void v8ByteIdenticalToV7() {
            var resp = FetchResponse.builder().throttleTimeMs(42).errorCode((short) 3).sessionId(11).topics(List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(0).errorCode((short) 0).highWatermark(100L).lastStableOffset(99L).logStartOffset(7L).abortedTransactions(List.of(new FetchResponse.AbortedTransaction(7L, 12L))).records(new byte[]{1, 2}).build())),
                    new FetchResponse.TopicResponse("other", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(1).errorCode((short) -1).highWatermark(42L).lastStableOffset(99L).logStartOffset(7L).abortedTransactions(null).records(null).build())))).build();

            byte[] v7 = FetchCodec.encodeResponse((short) 7, resp);
            byte[] v8 = FetchCodec.encodeResponse((short) 8, resp);

            assertArrayEquals(v7, v8, "v8 response must be byte-identical to v7");
        }

        @Test
        @DisplayName("v8 response round-trips through the v7 methods")
        void v8RoundTripThroughV7Methods() {
            var resp = FetchResponse.builder().throttleTimeMs(7).errorCode((short) 5).sessionId(11).topics(List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder().partitionIndex(3).errorCode((short) 5).highWatermark(999L).lastStableOffset(988L).logStartOffset(12L).abortedTransactions(List.of(new FetchResponse.AbortedTransaction(1L, 2L))).records(null).build())))).build();

            byte[] body = FetchCodec.encodeResponse((short) 8, resp);
            var decoded = FetchCodec.decodeResponse((short) 8, ByteBuffer.wrap(body));

            assertEquals(7, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            assertEquals((short) 5, decoded.errorCode(), "ErrorCode round-trip");
            assertEquals(11L, decoded.sessionId(), "SessionId round-trip");
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(3, p0.partitionIndex(), "PartitionIndex round-trip");
            assertNull(p0.records(), "null Records round-trip");
        }
    }

    @Nested
    @DisplayName("Fetch request v9 (key 1) — per-partition + CurrentLeaderEpoch after Partition")
    class RequestV9 {

        private static FetchRequest v9Request() {
            return FetchRequest.builder()
                    .replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(1)
                    .sessionId(11).sessionEpoch(3)
                    .topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            FetchRequest.PartitionFetch.builder()
                                    .partition(0).currentLeaderEpoch(5).fetchOffset(10L)
                                    .logStartOffset(42L).partitionMaxBytes(65536)
                                    .build()))))
                    .build();
        }

        @Test
        @DisplayName("v9 request round-trips with CurrentLeaderEpoch on the wire")
        void v9RoundTrip() {
            var req = FetchRequest.builder()
                    .replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(1)
                    .sessionId(11).sessionEpoch(3)
                    .topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            FetchRequest.PartitionFetch.builder()
                                    .partition(0).currentLeaderEpoch(5).fetchOffset(10L)
                                    .logStartOffset(42L).partitionMaxBytes(65536)
                                    .build()))))
                    .forgottenTopics(List.of(new FetchRequest.ForgottenTopic("topic", List.of(1, 2))))
                    .build();

            byte[] body = FetchCodec.encodeRequest((short) 9, req);
            var decoded = FetchCodec.decodeRequest((short) 9, ByteBuffer.wrap(body));

            assertEquals(-1, decoded.replicaId());
            assertEquals(500, decoded.maxWaitMs());
            assertEquals(1, decoded.minBytes());
            assertEquals(1048576, decoded.maxBytes());
            assertEquals(1, decoded.isolationLevel());
            assertEquals(11L, decoded.sessionId(), "SessionId (v7+) round-trip");
            assertEquals(3L, decoded.sessionEpoch(), "SessionEpoch (v7+) round-trip");
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(0, p0.partition());
            assertEquals(5, p0.currentLeaderEpoch(), "CurrentLeaderEpoch (v9+) round-trip");
            assertEquals(10L, p0.fetchOffset());
            assertEquals(42L, p0.logStartOffset(), "LogStartOffset (v5+) round-trip");
            assertEquals(65536, p0.partitionMaxBytes());
            assertEquals(1, decoded.forgottenTopics().size(), "ForgottenTopicsData count");
            assertEquals("topic", decoded.forgottenTopics().get(0).name());
            assertEquals(List.of(1, 2), decoded.forgottenTopics().get(0).partitions());
        }

        @Test
        @DisplayName("v9 request has the exact 72-byte spec wire layout (v7 + 4-byte CurrentLeaderEpoch)")
        void v9ExactBytes() {
            var req = v9Request();

            byte[] body = FetchCodec.encodeRequest((short) 9, req);
            // 4 (replicaId) + 4 (maxWaitMs) + 4 (minBytes) + 4 (maxBytes) + 1 (isolationLevel)
            // + 4 (sessionId, v7+) + 4 (sessionEpoch, v7+) + 4 (topic count) + 2 (name len)
            // + 5 (name) + 4 (partition count) + 4 (partition)
            // + 4 (currentLeaderEpoch, v9+) + 8 (fetchOffset) + 8 (logStartOffset, v5+)
            // + 4 (partitionMaxBytes) + 4 (forgotten count) = 72
            assertEquals(72, body.length, "exact v9 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(-1, buf.getInt(), "ReplicaId int32");
            assertEquals(500, buf.getInt(), "MaxWaitMs int32");
            assertEquals(1, buf.getInt(), "MinBytes int32");
            assertEquals(1048576, buf.getInt(), "MaxBytes int32");
            assertEquals(1, buf.get() & 0xff, "IsolationLevel int8 (v4+)");
            assertEquals(11L, buf.getInt(), "SessionId int32 (v7+)");
            assertEquals(3L, buf.getInt(), "SessionEpoch int32 (v7+)");
            assertEquals(1, buf.getInt(), "Topics count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "Partition int32");
            assertEquals(5, buf.getInt(), "CurrentLeaderEpoch int32 (v9+, after Partition)");
            assertEquals(10L, buf.getLong(), "FetchOffset int64");
            assertEquals(42L, buf.getLong(), "LogStartOffset int64 (v5+)");
            assertEquals(65536, buf.getInt(), "PartitionMaxBytes int32");
            assertEquals(0, buf.getInt(), "ForgottenTopicsData count int32 (v7+)");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v9 request is the v7 layout with 4-byte CurrentLeaderEpoch inserted after Partition")
        void v9IsV7PlusCurrentLeaderEpoch() {
            var req = v9Request();

            byte[] v7 = FetchCodec.encodeRequest((short) 7, req);
            byte[] v9 = FetchCodec.encodeRequest((short) 9, req);

            // v7 single-partition layout: header 40 (through Partition at 40..44) + 24-byte
            // per-partition tail + 4 (forgotten count) = 68. v9 inserts CurrentLeaderEpoch(4)
            // at offset 44, so v9 = 72.
            assertEquals(v7.length + 4, v9.length, "v9 is 4 bytes wider (one CurrentLeaderEpoch per partition)");
            // Everything up to and including the Partition int32 (0..44) is unchanged.
            assertArrayEquals(Arrays.copyOfRange(v7, 0, 44), Arrays.copyOfRange(v9, 0, 44),
                    "header through Partition unchanged");
            // The tail after the inserted CurrentLeaderEpoch (FetchOffset..ForgottenTopicsData)
            // is byte-identical.
            assertArrayEquals(Arrays.copyOfRange(v7, 44, v7.length),
                    Arrays.copyOfRange(v9, 48, v9.length),
                    "FetchOffset..ForgottenTopicsData byte-identical after the inserted CurrentLeaderEpoch");
        }

        @Test
        @DisplayName("v9 decode defaults currentLeaderEpoch to the carried value (v9+ is on the wire)")
        void v9DecodesCurrentLeaderEpoch() {
            // A consumer that never sends a leader epoch uses the builder default (-1); it still
            // round-trips the field on the v9 wire.
            var req = FetchRequest.builder()
                    .replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(0)
                    .topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            FetchRequest.PartitionFetch.builder()
                                    .partition(0).fetchOffset(10L).partitionMaxBytes(65536)
                                    .build()))))
                    .build();

            var decoded = FetchCodec.decodeRequest((short) 9, ByteBuffer.wrap(
                    FetchCodec.encodeRequest((short) 9, req)));
            assertEquals(-1, decoded.topics().get(0).partitions().get(0).currentLeaderEpoch(),
                    "absent/unknown CurrentLeaderEpoch round-trips as -1");
        }
    }

    @Nested
    @DisplayName("Fetch request v10 (key 1) — unchanged vs v9")
    class RequestV10 {

        @Test
        @DisplayName("v10 request is byte-identical to v9 (spec: no field change)")
        void v10ByteIdenticalToV9() {
            var req = FetchRequest.builder()
                    .replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(1)
                    .sessionId(11).sessionEpoch(3)
                    .topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            FetchRequest.PartitionFetch.builder()
                                    .partition(0).currentLeaderEpoch(5).fetchOffset(10L)
                                    .logStartOffset(42L).partitionMaxBytes(65536)
                                    .build()))))
                    .forgottenTopics(List.of(new FetchRequest.ForgottenTopic("topic", List.of(1, 2))))
                    .build();

            byte[] v9 = FetchCodec.encodeRequest((short) 9, req);
            byte[] v10 = FetchCodec.encodeRequest((short) 10, req);

            assertArrayEquals(v9, v10, "v10 request must be byte-identical to v9");
        }

        @Test
        @DisplayName("v10 request round-trips through the v9 methods")
        void v10RoundTripThroughV9Methods() {
            var req = FetchRequest.builder()
                    .replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(0)
                    .sessionId(11).sessionEpoch(3)
                    .topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            FetchRequest.PartitionFetch.builder()
                                    .partition(0).currentLeaderEpoch(7).fetchOffset(10L)
                                    .partitionMaxBytes(65536)
                                    .build()))))
                    .forgottenTopics(List.of(new FetchRequest.ForgottenTopic("other", List.of())))
                    .build();

            byte[] body = FetchCodec.encodeRequest((short) 10, req);
            var decoded = FetchCodec.decodeRequest((short) 10, ByteBuffer.wrap(body));

            assertEquals(11L, decoded.sessionId(), "SessionId round-trip");
            assertEquals(3L, decoded.sessionEpoch(), "SessionEpoch round-trip");
            assertEquals(7, decoded.topics().get(0).partitions().get(0).currentLeaderEpoch(),
                    "CurrentLeaderEpoch (v9+) round-trips at v10");
            assertEquals(1, decoded.forgottenTopics().size(), "ForgottenTopicsData round-trip");
            assertEquals("other", decoded.forgottenTopics().get(0).name());
        }
    }

    @Nested
    @DisplayName("Fetch response v9 (key 1) — unchanged vs v7/v8")
    class ResponseV9 {

        @Test
        @DisplayName("v9 response is byte-identical to v7 (spec: v9 changes the request only)")
        void v9ByteIdenticalToV7() {
            var resp = FetchResponse.builder()
                    .throttleTimeMs(42).errorCode((short) 3).sessionId(11)
                    .topics(List.of(
                            new FetchResponse.TopicResponse("topic", List.of(
                                    FetchResponse.PartitionResponse.builder()
                                            .partitionIndex(0).errorCode((short) 0).highWatermark(100L)
                                            .lastStableOffset(99L).logStartOffset(7L)
                                            .abortedTransactions(List.of(new FetchResponse.AbortedTransaction(7L, 12L)))
                                            .records(new byte[]{1, 2})
                                            .build())),
                            new FetchResponse.TopicResponse("other", List.of(
                                    FetchResponse.PartitionResponse.builder()
                                            .partitionIndex(1).errorCode((short) -1).highWatermark(42L)
                                            .build()))))
                    .build();

            byte[] v7 = FetchCodec.encodeResponse((short) 7, resp);
            byte[] v9 = FetchCodec.encodeResponse((short) 9, resp);

            assertArrayEquals(v7, v9, "v9 response must be byte-identical to v7");
        }

        @Test
        @DisplayName("v9 response round-trips through the v7 methods")
        void v9RoundTripThroughV7Methods() {
            var resp = FetchResponse.builder()
                    .throttleTimeMs(7).errorCode((short) 3).sessionId(11)
                    .topics(List.of(new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder()
                                    .partitionIndex(3).errorCode((short) 5).highWatermark(999L)
                                    .lastStableOffset(988L).logStartOffset(12L)
                                    .abortedTransactions(List.of(new FetchResponse.AbortedTransaction(1L, 2L)))
                                    .build()))))
                    .build();

            byte[] body = FetchCodec.encodeResponse((short) 9, resp);
            var decoded = FetchCodec.decodeResponse((short) 9, ByteBuffer.wrap(body));

            assertEquals(7, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            assertEquals((short) 3, decoded.errorCode(), "top-level ErrorCode (v7+) round-trip");
            assertEquals(11L, decoded.sessionId(), "top-level SessionId (v7+) round-trip");
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(3, p0.partitionIndex(), "PartitionIndex round-trip");
            assertEquals((short) 5, p0.errorCode(), "partition ErrorCode round-trip");
            assertEquals(999L, p0.highWatermark(), "HighWatermark round-trip");
            assertEquals(988L, p0.lastStableOffset(), "LastStableOffset (v4+) round-trip");
            assertEquals(12L, p0.logStartOffset(), "LogStartOffset (v5+) round-trip");
            assertNull(p0.records(), "null Records round-trip");
        }
    }

    @Nested
    @DisplayName("Fetch response v10 (key 1) — unchanged vs v7/v8/v9")
    class ResponseV10 {

        @Test
        @DisplayName("v10 response is byte-identical to v7 and v9 (spec: no field change)")
        void v10ByteIdenticalToV7AndV9() {
            var resp = FetchResponse.builder()
                    .throttleTimeMs(42).errorCode((short) 3).sessionId(11)
                    .topics(List.of(new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder()
                                    .partitionIndex(0).errorCode((short) 0).highWatermark(100L)
                                    .lastStableOffset(99L).logStartOffset(7L)
                                    .abortedTransactions(List.of(new FetchResponse.AbortedTransaction(7L, 12L)))
                                    .records(new byte[]{1, 2})
                                    .build()))))
                    .build();

            byte[] v7 = FetchCodec.encodeResponse((short) 7, resp);
            byte[] v9 = FetchCodec.encodeResponse((short) 9, resp);
            byte[] v10 = FetchCodec.encodeResponse((short) 10, resp);

            assertArrayEquals(v7, v9, "v9 response must equal v7");
            assertArrayEquals(v7, v10, "v10 response must equal v7");
        }

        @Test
        @DisplayName("v10 response round-trips through the v7 methods")
        void v10RoundTripThroughV7Methods() {
            var resp = FetchResponse.builder()
                    .throttleTimeMs(7).errorCode((short) 5).sessionId(11)
                    .topics(List.of(new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder()
                                    .partitionIndex(3).errorCode((short) 5).highWatermark(999L)
                                    .lastStableOffset(988L).logStartOffset(12L)
                                    .abortedTransactions(List.of(new FetchResponse.AbortedTransaction(1L, 2L)))
                                    .build()))))
                    .build();

            byte[] body = FetchCodec.encodeResponse((short) 10, resp);
            var decoded = FetchCodec.decodeResponse((short) 10, ByteBuffer.wrap(body));

            assertEquals(7, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            assertEquals((short) 5, decoded.errorCode(), "ErrorCode round-trip");
            assertEquals(11L, decoded.sessionId(), "SessionId round-trip");
            assertEquals(3, decoded.topics().get(0).partitions().get(0).partitionIndex(), "PartitionIndex round-trip");
            assertNull(decoded.topics().get(0).partitions().get(0).records(), "null Records round-trip");
        }
    }

    @Nested
    @DisplayName("Fetch request v11 (key 1) — + trailing RackId(string) after ForgottenTopicsData")
    class RequestV11 {

        private static FetchRequest v11Request() {
            return FetchRequest.builder()
                    .replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(1)
                    .sessionId(11).sessionEpoch(3)
                    .topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            FetchRequest.PartitionFetch.builder()
                                    .partition(0).currentLeaderEpoch(5).fetchOffset(10L)
                                    .logStartOffset(42L).partitionMaxBytes(65536)
                                    .build()))))
                    .rackId("rack1")
                    .build();
        }

        @Test
        @DisplayName("v11 request round-trips with RackId on the wire")
        void v11RoundTrip() {
            var req = FetchRequest.builder()
                    .replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(1)
                    .sessionId(11).sessionEpoch(3)
                    .topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            FetchRequest.PartitionFetch.builder()
                                    .partition(0).currentLeaderEpoch(5).fetchOffset(10L)
                                    .logStartOffset(42L).partitionMaxBytes(65536)
                                    .build()))))
                    .forgottenTopics(List.of(new FetchRequest.ForgottenTopic("topic", List.of(1, 2))))
                    .rackId("rack1")
                    .build();

            byte[] body = FetchCodec.encodeRequest((short) 11, req);
            var decoded = FetchCodec.decodeRequest((short) 11, ByteBuffer.wrap(body));

            assertEquals(-1, decoded.replicaId());
            assertEquals(500, decoded.maxWaitMs());
            assertEquals(1, decoded.minBytes());
            assertEquals(1048576, decoded.maxBytes());
            assertEquals(1, decoded.isolationLevel());
            assertEquals(11L, decoded.sessionId(), "SessionId (v7+) round-trip");
            assertEquals(3L, decoded.sessionEpoch(), "SessionEpoch (v7+) round-trip");
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(0, p0.partition());
            assertEquals(5, p0.currentLeaderEpoch(), "CurrentLeaderEpoch (v9+) round-trip");
            assertEquals(10L, p0.fetchOffset());
            assertEquals(42L, p0.logStartOffset(), "LogStartOffset (v5+) round-trip");
            assertEquals(65536, p0.partitionMaxBytes());
            assertEquals(1, decoded.forgottenTopics().size(), "ForgottenTopicsData count");
            assertEquals("topic", decoded.forgottenTopics().get(0).name());
            assertEquals(List.of(1, 2), decoded.forgottenTopics().get(0).partitions());
            assertEquals("rack1", decoded.rackId(), "RackId (v11+) round-trip");
        }

        @Test
        @DisplayName("v11 request has the exact 79-byte spec wire layout (v9 + 2-byte length + 5 RackId)")
        void v11ExactBytes() {
            var req = v11Request();

            byte[] body = FetchCodec.encodeRequest((short) 11, req);
            // 4 (replicaId) + 4 (maxWaitMs) + 4 (minBytes) + 4 (maxBytes) + 1 (isolationLevel)
            // + 4 (sessionId, v7+) + 4 (sessionEpoch, v7+) + 4 (topic count) + 2 (name len)
            // + 5 (name) + 4 (partition count) + 4 (partition) + 4 (currentLeaderEpoch, v9+)
            // + 8 (fetchOffset) + 8 (logStartOffset, v5+) + 4 (partitionMaxBytes)
            // + 4 (forgotten count) + 2 (RackId string16 length, v11+) + 5 (RackId, v11+) = 79
            assertEquals(79, body.length, "exact v11 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(-1, buf.getInt(), "ReplicaId int32");
            assertEquals(500, buf.getInt(), "MaxWaitMs int32");
            assertEquals(1, buf.getInt(), "MinBytes int32");
            assertEquals(1048576, buf.getInt(), "MaxBytes int32");
            assertEquals(1, buf.get() & 0xff, "IsolationLevel int8 (v4+)");
            assertEquals(11L, buf.getInt(), "SessionId int32 (v7+)");
            assertEquals(3L, buf.getInt(), "SessionEpoch int32 (v7+)");
            assertEquals(1, buf.getInt(), "Topics count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(0, buf.getInt(), "Partition int32");
            assertEquals(5, buf.getInt(), "CurrentLeaderEpoch int32 (v9+)");
            assertEquals(10L, buf.getLong(), "FetchOffset int64");
            assertEquals(42L, buf.getLong(), "LogStartOffset int64 (v5+)");
            assertEquals(65536, buf.getInt(), "PartitionMaxBytes int32");
            assertEquals(0, buf.getInt(), "ForgottenTopicsData count int32 (v7+)");
            assertEquals(5, buf.getShort(), "RackId string16 length (v11+, after ForgottenTopicsData)");
            byte[] rack = new byte[5];
            buf.get(rack);
            assertEquals("rack1", new String(rack, StandardCharsets.UTF_8), "RackId value");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v11 request is the v9 layout with RackId(string) appended after ForgottenTopicsData")
        void v11IsV9PlusRackId() {
            var req = v11Request();

            byte[] v9 = FetchCodec.encodeRequest((short) 9, req);
            byte[] v11 = FetchCodec.encodeRequest((short) 11, req);

            // v11 appends 2 (RackId length) + 5 (RackId) = 7 bytes after the ForgottenTopicsData
            // array; everything before it is byte-identical.
            assertEquals(v9.length + 7, v11.length, "v11 is 7 bytes wider (RackId length + value)");
            assertArrayEquals(v9, Arrays.copyOfRange(v11, 0, v9.length),
                    "v9 body byte-identical at the front of v11");
            ByteBuffer buf = ByteBuffer.wrap(v11);
            buf.position(v9.length);
            assertEquals(5, buf.getShort(), "RackId string16 length");
            byte[] rack = new byte[5];
            buf.get(rack);
            assertEquals("rack1", new String(rack, StandardCharsets.UTF_8), "RackId value");
        }

        @Test
        @DisplayName("v11 decode defaults rackId to the carried value; null encodes as \"\" (spec default)")
        void v11DecodesRackIdDefault() {
            // A consumer with no rack set leaves the builder default (\"\" = the spec default);
            // it still round-trips the field on the v11 wire.
            var req = FetchRequest.builder()
                    .replicaId(-1).maxWaitMs(500).minBytes(1).maxBytes(1048576).isolationLevel(0)
                    .topics(List.of(new FetchRequest.TopicFetch("topic", List.of(
                            FetchRequest.PartitionFetch.builder()
                                    .partition(0).fetchOffset(10L).partitionMaxBytes(65536)
                                    .build()))))
                    .build();

            var decoded = FetchCodec.decodeRequest((short) 11, ByteBuffer.wrap(
                    FetchCodec.encodeRequest((short) 11, req)));
            assertEquals("", decoded.rackId(), "absent RackId round-trips as the empty string");
        }
    }

    @Nested
    @DisplayName("Fetch response v11 (key 1) — per-partition + PreferredReadReplica after AbortedTransactions")
    class ResponseV11 {

        private static FetchResponse v11Response() {
            return FetchResponse.builder()
                    .throttleTimeMs(7).errorCode((short) 3).sessionId(11)
                    .topics(List.of(new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder()
                                    .partitionIndex(3).errorCode((short) 5).highWatermark(999L)
                                    .lastStableOffset(988L).logStartOffset(12L)
                                    .abortedTransactions(List.of(new FetchResponse.AbortedTransaction(1L, 2L)))
                                    .preferredReadReplica(2)
                                    .build()))))
                    .build();
        }

        @Test
        @DisplayName("v11 response round-trips with PreferredReadReplica on the wire")
        void v11RoundTrip() {
            var resp = v11Response();

            byte[] body = FetchCodec.encodeResponse((short) 11, resp);
            var decoded = FetchCodec.decodeResponse((short) 11, ByteBuffer.wrap(body));

            assertEquals(7, decoded.throttleTimeMs(), "ThrottleTimeMs round-trip");
            assertEquals((short) 3, decoded.errorCode(), "top-level ErrorCode (v7+) round-trip");
            assertEquals(11L, decoded.sessionId(), "top-level SessionId (v7+) round-trip");
            var p0 = decoded.topics().get(0).partitions().get(0);
            assertEquals(3, p0.partitionIndex(), "PartitionIndex round-trip");
            assertEquals((short) 5, p0.errorCode(), "partition ErrorCode round-trip");
            assertEquals(999L, p0.highWatermark(), "HighWatermark round-trip");
            assertEquals(988L, p0.lastStableOffset(), "LastStableOffset (v4+) round-trip");
            assertEquals(12L, p0.logStartOffset(), "LogStartOffset (v5+) round-trip");
            assertEquals(1, p0.abortedTransactions().size(), "AbortedTransactions (v4+) round-trip");
            assertEquals(1L, p0.abortedTransactions().get(0).producerId());
            assertEquals(2L, p0.abortedTransactions().get(0).firstOffset());
            assertEquals(2, p0.preferredReadReplica(), "PreferredReadReplica (v11+) round-trip");
        }

        @Test
        @DisplayName("v11 response has the exact 83-byte spec wire layout (v7 + 4-byte PreferredReadReplica)")
        void v11ExactBytes() {
            var resp = v11Response();

            byte[] body = FetchCodec.encodeResponse((short) 11, resp);
            // ThrottleTimeMs 4 + ErrorCode 2 + SessionId 4 = 10; responses count 4 + topic 7
            // (len 2 + "topic" 5) + partition count 4 = 25; per-partition: idx 4 + err 2 +
            // hwm 8 + lso 8 + logStart 8 + abortedCount 4 + 12 (1 aborted entry) +
            // PreferredReadReplica 4 (v11+) + Records 4 (null length) = 58; 25 + 58 = 83.
            assertEquals(83, body.length, "exact v11 wire layout");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(7, buf.getInt(), "ThrottleTimeMs int32");
            assertEquals((short) 3, buf.getShort(), "ErrorCode int16 (v7+)");
            assertEquals(11L, buf.getInt(), "SessionId int32 (v7+)");
            assertEquals(1, buf.getInt(), "Responses count int32");
            assertEquals(5, buf.getShort(), "Topic string16 length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", new String(name, StandardCharsets.UTF_8), "Topic name");
            assertEquals(1, buf.getInt(), "Partitions count int32");
            assertEquals(3, buf.getInt(), "PartitionIndex int32");
            assertEquals((short) 5, buf.getShort(), "Partition ErrorCode int16");
            assertEquals(999L, buf.getLong(), "HighWatermark int64");
            assertEquals(988L, buf.getLong(), "LastStableOffset int64 (v4+)");
            assertEquals(12L, buf.getLong(), "LogStartOffset int64 (v5+)");
            assertEquals(1, buf.getInt(), "AbortedTransactions count int32 (v4+)");
            assertEquals(1L, buf.getLong(), "AbortedTransaction ProducerId int64");
            assertEquals(2L, buf.getLong(), "AbortedTransaction FirstOffset int64");
            assertEquals(2, buf.getInt(), "PreferredReadReplica int32 (v11+, after AbortedTransactions)");
            assertEquals(-1, buf.getInt(), "Records length int32 (null)");
            assertEquals(0, buf.remaining(), "no trailing bytes");
        }

        @Test
        @DisplayName("v11 response is the v7 layout with 4-byte PreferredReadReplica inserted per partition")
        void v11IsV7PlusPreferredReadReplica() {
            var resp = v11Response();

            byte[] v7 = FetchCodec.encodeResponse((short) 7, resp);
            byte[] v11 = FetchCodec.encodeResponse((short) 11, resp);

            // v7 is 79 bytes; v11 inserts PreferredReadReplica(4) after the AbortedTransactions
            // array: prefix 0..75 (through the 12-byte aborted entry at 63..75) unchanged;
            // the Records length field (the last 4 bytes of v7, at 75..79) matches the v11
            // tail at 79..83.
            assertEquals(v7.length + 4, v11.length, "v11 is 4 bytes wider (one PreferredReadReplica per partition)");
            assertArrayEquals(Arrays.copyOfRange(v7, 0, 75), Arrays.copyOfRange(v11, 0, 75),
                    "header through AbortedTransactions unchanged");
            assertEquals(2, ByteBuffer.wrap(v11, 75, 4).getInt(), "PreferredReadReplica int32 at the inserted slot");
            assertArrayEquals(Arrays.copyOfRange(v7, 75, v7.length),
                    Arrays.copyOfRange(v11, 79, v11.length),
                    "Records field byte-identical after the inserted PreferredReadReplica");
        }

        @Test
        @DisplayName("v11 decode defaults preferredReadReplica to the carried value (-1 absent round-trips)")
        void v11DecodesPreferredReadReplicaDefault() {
            // A partition with no preferred replica carries the builder default (-1); it still
            // round-trips the field on the v11 wire.
            var resp = FetchResponse.builder()
                    .throttleTimeMs(7).errorCode((short) 3).sessionId(11)
                    .topics(List.of(new FetchResponse.TopicResponse("topic", List.of(
                            FetchResponse.PartitionResponse.builder()
                                    .partitionIndex(0).errorCode((short) 0).highWatermark(100L)
                                    .build()))))
                    .build();

            var decoded = FetchCodec.decodeResponse((short) 11, ByteBuffer.wrap(
                    FetchCodec.encodeResponse((short) 11, resp)));
            assertEquals(-1, decoded.topics().get(0).partitions().get(0).preferredReadReplica(),
                    "absent PreferredReadReplica round-trips as -1");
        }
    }

    @Nested
    @DisplayName("Version dispatch")
    class Dispatch {

        @Test
        @DisplayName("request v12 encode throws CodecNotImplementedException (next unimplemented)")
        void v12RequestEncodeNotImplemented() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 0,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.encodeRequest((short) 12, req));
        }

        @Test
        @DisplayName("request v12 decode throws CodecNotImplementedException (next unimplemented)")
        void v12RequestDecodeNotImplemented() {
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.decodeRequest((short) 12, ByteBuffer.wrap(new byte[0])));
        }

        @Test
        @DisplayName("request v16 encode throws CodecNotImplementedException (beyond spec max v15)")
        void v16RequestEncodeNotImplemented() {
            var req = new FetchRequest(-1, 500, 1, 1048576,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.encodeRequest((short) 16, req));
        }

        @Test
        @DisplayName("response v12 encode throws CodecNotImplementedException (next unimplemented)")
        void v12ResponseEncodeNotImplemented() {
            var resp = new FetchResponse(0, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 0L, null)))));
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.encodeResponse((short) 12, resp));
        }

        @Test
        @DisplayName("response v12 decode throws CodecNotImplementedException (next unimplemented)")
        void v12ResponseDecodeNotImplemented() {
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.decodeResponse((short) 12, ByteBuffer.wrap(new byte[0])));
        }

        @Test
        @DisplayName("response v16 decode throws CodecNotImplementedException (beyond spec max v15)")
        void v16ResponseDecodeNotImplemented() {
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.decodeResponse((short) 16, ByteBuffer.wrap(new byte[0])));
        }
    }
}
