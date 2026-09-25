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
 *   <li>Request/response v4+ throw {@link CodecNotImplementedException} until
 *       their own sub-task rows land (v4 adds IsolationLevel to the request and
 *       LastStableOffset/AbortedTransactions to the response).</li>
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
    @DisplayName("Version dispatch")
    class Dispatch {

        @Test
        @DisplayName("request v4 encode throws CodecNotImplementedException (next unimplemented)")
        void v4RequestEncodeNotImplemented() {
            var req = new FetchRequest(-1, 500, 1, 1048576,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.encodeRequest((short) 4, req));
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
        @DisplayName("response v4 encode throws CodecNotImplementedException (next unimplemented)")
        void v4ResponseEncodeNotImplemented() {
            var resp = new FetchResponse(0, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 0L, null)))));
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.encodeResponse((short) 4, resp));
        }

        @Test
        @DisplayName("response v4 decode throws CodecNotImplementedException (next unimplemented)")
        void v4ResponseDecodeNotImplemented() {
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.decodeResponse((short) 4, ByteBuffer.wrap(new byte[0])));
        }

        @Test
        @DisplayName("response v16 decode throws CodecNotImplementedException (beyond spec max v15)")
        void v16ResponseDecodeNotImplemented() {
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.decodeResponse((short) 16, ByteBuffer.wrap(new byte[0])));
        }
    }
}
