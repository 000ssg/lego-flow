package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.FetchRequest;
import ssg.legoflow.messaging.kafka.protocol.FetchResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
 *   <li>Response v1+ and request v3+ throw {@link CodecNotImplementedException} until
 *       their own sub-task rows land.</li>
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
    @DisplayName("Version dispatch")
    class Dispatch {

        @Test
        @DisplayName("request v3 encode throws CodecNotImplementedException (next unimplemented)")
        void v3RequestEncodeNotImplemented() {
            var req = new FetchRequest(-1, 500, 1, 1048576,
                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                            new FetchRequest.PartitionFetch(0, 10L, 65536)))));
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.encodeRequest((short) 3, req));
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
        @DisplayName("response v1 encode throws CodecNotImplementedException (next unimplemented)")
        void v1ResponseEncodeNotImplemented() {
            var resp = new FetchResponse(0, List.of(
                    new FetchResponse.TopicResponse("topic", List.of(
                            new FetchResponse.PartitionResponse(0, (short) 0, 0L, null)))));
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.encodeResponse((short) 1, resp));
        }

        @Test
        @DisplayName("response v16 decode throws CodecNotImplementedException (beyond spec max v15)")
        void v16ResponseDecodeNotImplemented() {
            assertThrows(CodecNotImplementedException.class,
                    () -> FetchCodec.decodeResponse((short) 16, ByteBuffer.wrap(new byte[0])));
        }
    }
}
