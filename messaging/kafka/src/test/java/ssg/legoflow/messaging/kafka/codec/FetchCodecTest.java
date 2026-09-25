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
 *   <li>Request/response v7+ throw {@link CodecNotImplementedException} until
 *       their own sub-task rows land (v7 adds SessionId/SessionEpoch and
 *       ForgottenTopicsData).</li>
 * </ul>
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
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L,
                                    99L, 7L,
                                    List.of(new FetchResponse.AbortedTransaction(7L, 12L)),
                                    new byte[]{1, 2})))));

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
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L,
                                    99L, 7L,
                                    List.of(new FetchResponse.AbortedTransaction(7L, 12L)),
                                    new byte[]{1, 2})))));

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
                            new FetchResponse.PartitionResponse(0, (short) 0, 10L, 99L, 7L,
                                    null, null)))));

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
                            new FetchResponse.PartitionResponse(0, (short) 0, 100L,
                                    99L, 7L,
                                    List.of(new FetchResponse.AbortedTransaction(7L, 12L)),
                                    new byte[]{1, 2}))),
                    new FetchResponse.TopicResponse("other", List.of(
                            new FetchResponse.PartitionResponse(1, (short) -1, 42L, 99L, 7L,
                                    null, null)))));

            byte[] v5 = FetchCodec.encodeResponse((short) 5, resp);
            byte[] v6 = FetchCodec.encodeResponse((short) 6, resp);

            assertArrayEquals(v5, v6, "v6 response must be byte-identical to v5");
        }

        @Test
        @DisplayName("v6 response round-trips through the v5 methods")
        void v6RoundTripThroughV5Methods() {
            var resp = new FetchResponse(7, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(3, (short) 5, 999L, 988L, 12L,
                                    List.of(new FetchResponse.AbortedTransaction(1L, 2L)), null)))));

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
    @DisplayName("Version dispatch")
    class Dispatch {

        @Test
        @DisplayName("request v7 encode throws CodecNotImplementedException (next unimplemented)")
        void v7RequestEncodeNotImplemented() {
            var req = new FetchRequest(-1, 500, 1, 1048576, 0,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.encodeRequest((short) 7, req));
        }

        @Test
        @DisplayName("request v7 decode throws CodecNotImplementedException (next unimplemented)")
        void v7RequestDecodeNotImplemented() {
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.decodeRequest((short) 7, ByteBuffer.wrap(new byte[0])));
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
        @DisplayName("response v7 encode throws CodecNotImplementedException (next unimplemented)")
        void v7ResponseEncodeNotImplemented() {
            var resp = new FetchResponse(0, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 0L, null)))));
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.encodeResponse((short) 7, resp));
        }

        @Test
        @DisplayName("response v7 decode throws CodecNotImplementedException (next unimplemented)")
        void v7ResponseDecodeNotImplemented() {
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.decodeResponse((short) 7, ByteBuffer.wrap(new byte[0])));
        }

        @Test
        @DisplayName("response v16 decode throws CodecNotImplementedException (beyond spec max v15)")
        void v16ResponseDecodeNotImplemented() {
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.decodeResponse((short) 16, ByteBuffer.wrap(new byte[0])));
        }
    }
}
