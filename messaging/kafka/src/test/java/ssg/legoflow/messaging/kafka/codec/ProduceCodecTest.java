package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.ProduceRequest;
import ssg.legoflow.messaging.kafka.protocol.ProduceResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Produce (API key 0) v0 unit tests — Phase 6a, Record I/O, first row.
 *
 * <p>Layout expectations are taken from the Kafka 3.6.1 schema
 * ({@code doc/spec/message/Produce{Request,Response}.json}):
 * <ul>
 *   <li>Request v0: Acks(int16) + TimeoutMs(int32) + TopicData[](Name(string16) +
 *       PartitionData[](Index(int32) + Records(Nullable bytes: -1 = absent, else
 *       int32 length + bytes))). <b>No</b> TransactionalId (a v3+ field — its presence
 *       in the earlier inline codec was a layout bug: a v3-shaped body under a v0 frame).</li>
 *   <li>Response v0: TopicData[](Name(string16) + PartitionResponse[](Index(int32) +
 *       ErrorCode(int16) + BaseOffset(int64))). <b>No</b> ThrottleTimeMs (v1+) and no
 *       LogAppendTimeMs (v2+): carried model values are discarded on encode; decoded
 *       values are defaulted (0 / -1).</li>
 *   <li>v1+ throw {@link CodecNotImplementedException} (no silent fall-through).</li>
 * </ul>
 */
class ProduceCodecTest {

    @Nested
    @DisplayName("Produce request (key 0)")
    class Request {

        @Test
        @DisplayName("v0 round-trips acks/timeout/topics with records")
        void v0RoundTrip() {
            var req = new ProduceRequest(null, (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] body = ProduceCodec.encodeRequest((short) 0, req);
            var decoded = ProduceCodec.decodeRequest((short) 0, ByteBuffer.wrap(body));

            assertNull(decoded.transactionalId(), "v0 has no TransactionalId field");
            assertEquals((short) -1, decoded.acks());
            assertEquals(30000, decoded.timeoutMs());
            assertEquals(1, decoded.topicData().size());
            assertEquals("topic", decoded.topicData().getFirst().name());
            assertArrayEquals(new byte[]{1, 2, 3},
                    decoded.topicData().getFirst().partitionData().getFirst().records());
        }

        @Test
        @DisplayName("v0 exact byte layout — no leading TransactionalId (spec field order)")
        void v0ExactBytes() {
            var req = new ProduceRequest(null, (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] body = ProduceCodec.encodeRequest((short) 0, req);

            // Acks(2) + Timeout(4) + topicCount(4) + name(2+5) + partCount(4) + index(4) + records(4+3) = 32
            assertEquals(32, body.length, "2+4+4+(2+5)+4+4+(4+3)");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals((short) -1, buf.getShort(), "Acks");
            assertEquals(30000, buf.getInt(), "TimeoutMs");
            assertEquals(1, buf.getInt(), "TopicData count");
            assertEquals(5, buf.getShort(), "Name length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", StandardCharsets.UTF_8.decode(ByteBuffer.wrap(name)).toString(), "Name");
            assertEquals(1, buf.getInt(), "PartitionData count");
            assertEquals(0, buf.getInt(), "Partition index");
            assertEquals(3, buf.getInt(), "Records length");
            byte[] records = new byte[3];
            buf.get(records);
            assertArrayEquals(new byte[]{1, 2, 3}, records, "Records");
            assertEquals(0, buf.remaining(), "body must be exactly consumed");
        }

        @Test
        @DisplayName("v0 null records encode as length -1 (spec: Records is Nullable bytes)")
        void v0NullRecords() {
            var req = new ProduceRequest(null, (short) 1, 5000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(7, null)))));

            byte[] body = ProduceCodec.encodeRequest((short) 0, req);
            // 2(acks)+4(timeout)+4(topicCount)+(2+1)name+4(partCount)+4(index)+4(recordsLen -1) = 25
            assertEquals(25, body.length);
            ByteBuffer buf = ByteBuffer.wrap(body);
            buf.getShort(); // Acks
            buf.getInt();   // TimeoutMs
            buf.getInt();   // topicCount
            buf.getShort(); // name length
            buf.get();      // name
            buf.getInt();   // partCount
            assertEquals(7, buf.getInt(), "partition index");
            assertEquals(-1, buf.getInt(), "records length -1 = absent");
            assertEquals(0, buf.remaining());

            var decoded = ProduceCodec.decodeRequest((short) 0, ByteBuffer.wrap(body));
            assertNull(decoded.topicData().getFirst().partitionData().getFirst().records());
        }

        @Test
        @DisplayName("v0 round-trips multiple topics and partitions with mixed records presence")
        void v0MultipleTopics() {
            var req = new ProduceRequest(null, (short) -1, 1000,
                    List.of(
                            new ProduceRequest.TopicData("t1", List.of(
                                    new ProduceRequest.PartitionData(0, new byte[]{1}),
                                    new ProduceRequest.PartitionData(1, null))),
                            new ProduceRequest.TopicData("t2", List.of(
                                    new ProduceRequest.PartitionData(0, new byte[]{3, 4})))));

            byte[] body = ProduceCodec.encodeRequest((short) 0, req);
            var decoded = ProduceCodec.decodeRequest((short) 0, ByteBuffer.wrap(body));

            assertEquals(2, decoded.topicData().size());
            assertEquals(2, decoded.topicData().get(0).partitionData().size());
            assertArrayEquals(new byte[]{1}, decoded.topicData().get(0).partitionData().get(0).records());
            assertNull(decoded.topicData().get(0).partitionData().get(1).records());
            assertArrayEquals(new byte[]{3, 4}, decoded.topicData().get(1).partitionData().get(0).records());
        }
    }

    @Nested
    @DisplayName("Produce response (key 0)")
    class Response {

        @Test
        @DisplayName("v0 round-trips topics/partitions with error and offset")
        void v0RoundTrip() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 42L, 1234567890L)))), 100);

            byte[] body = ProduceCodec.encodeResponse((short) 0, resp);
            var decoded = ProduceCodec.decodeResponse((short) 0, ByteBuffer.wrap(body));

            assertEquals(1, decoded.responses().size());
            assertEquals("topic", decoded.responses().getFirst().name());
            var pr = decoded.responses().getFirst().partitionResponses().getFirst();
            assertEquals(0, pr.partitionIndex());
            assertEquals((short) 0, pr.errorCode());
            assertEquals(42L, pr.baseOffset());
            assertEquals(-1L, pr.logAppendTimeMs(), "v0 has no LogAppendTimeMs — default -1");
            assertEquals(0, decoded.throttleTimeMs(), "v0 has no ThrottleTimeMs — default 0");
        }

        @Test
        @DisplayName("v0 exact byte layout — no LogAppendTimeMs, no trailing ThrottleTimeMs")
        void v0ExactBytes() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(3, (short) 5, 100L, 999L)))), 777);

            byte[] body = ProduceCodec.encodeResponse((short) 0, resp);

            // topicCount(4) + name(2+5) + partCount(4) + (index 4 + error 2 + offset 8) = 29
            assertEquals(29, body.length, "4+(2+5)+4+14");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(1, buf.getInt(), "TopicData count");
            assertEquals(5, buf.getShort(), "Name length");
            byte[] name = new byte[5];
            buf.get(name);
            assertEquals("topic", StandardCharsets.UTF_8.decode(ByteBuffer.wrap(name)).toString(), "Name");
            assertEquals(1, buf.getInt(), "PartitionResponse count");
            assertEquals(3, buf.getInt(), "Partition index");
            assertEquals((short) 5, buf.getShort(), "ErrorCode");
            assertEquals(100L, buf.getLong(), "BaseOffset");
            assertEquals(0, buf.remaining(), "no LogAppendTimeMs / ThrottleTimeMs at v0");
        }

        @Test
        @DisplayName("v0 decodes an error partition and defaults the absent fields")
        void v0ErrorPartition() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t", List.of(
                            new ProduceResponse.PartitionResponse(2, KafkaErrorsForTest.UNKNOWN_TOPIC, -1L, 0L)))), 0);

            byte[] body = ProduceCodec.encodeResponse((short) 0, resp);
            var decoded = ProduceCodec.decodeResponse((short) 0, ByteBuffer.wrap(body));

            var pr = decoded.responses().getFirst().partitionResponses().getFirst();
            assertEquals(2, pr.partitionIndex());
            assertEquals(KafkaErrorsForTest.UNKNOWN_TOPIC, pr.errorCode());
            assertEquals(-1L, pr.baseOffset());
        }
    }

    @Nested
    @DisplayName("Version dispatch")
    class Dispatch {

        @Test
        @DisplayName("v1 request throws CodecNotImplementedException (not a silent fall-through)")
        void v1RequestNotImplemented() {
            var req = new ProduceRequest(null, (short) 1, 1000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1})))));
            assertThrows(CodecNotImplementedException.class,
                    () -> ProduceCodec.encodeRequest((short) 1, req));
        }

        @Test
        @DisplayName("v3 request throws CodecNotImplementedException")
        void v3RequestNotImplemented() {
            var req = new ProduceRequest("txn", (short) 1, 1000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1})))));
            assertThrows(CodecNotImplementedException.class,
                    () -> ProduceCodec.encodeRequest((short) 3, req));
        }

        @Test
        @DisplayName("v1 response throws CodecNotImplementedException")
        void v1ResponseNotImplemented() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 0L, 0L)))), 0);
            assertThrows(CodecNotImplementedException.class,
                    () -> ProduceCodec.encodeResponse((short) 1, resp));
        }

        @Test
        @DisplayName("v1 decode throws CodecNotImplementedException")
        void v1DecodeNotImplemented() {
            var req = new ProduceRequest(null, (short) 1, 1000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1})))));
            byte[] body = ProduceCodec.encodeRequest((short) 0, req);
            assertThrows(CodecNotImplementedException.class,
                    () -> ProduceCodec.decodeRequest((short) 1, ByteBuffer.wrap(body)));
        }
    }

    /** Local alias for a Kafka error code used by the response tests (avoids broker-package import). */
    private static final class KafkaErrorsForTest {
        static final short UNKNOWN_TOPIC = 3;
    }
}
