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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 *   <li>v1: request byte-identical to v0 ("Version 1 and 2 are the same as version 0");
 *       response = v0 layout + trailing ThrottleTimeMs(int32) after the TopicData array
 *       (LogAppendTimeMs is a v2+ field — still absent in the v1 partition).</li>
 *   <li>v2: request still byte-identical to v0 (TransactionalId is v3+); each response
 *       partition gains LogAppendTimeMs(int64) after BaseOffset (spec default -1), the
 *       trailing ThrottleTimeMs(int32) is retained from v1.</li>
 *   <li>v3: the request gains a leading nullable TransactionalId(string) before Acks —
 *       the first request framing change since v0 (dedicated V3 methods); the response
 *       is unchanged vs v2 (LogStartOffset is v5+) and shares the v2 methods.</li>
 *   <li>v4: unchanged in both directions (no field version range differs at v4+ vs v3)
 *       — all four dispatches fall through to the v3 methods.</li>
 *   <li>v5: request unchanged vs v3 (falls through to the v3 methods); the response
 *       partition gains LogStartOffset(int64) after LogAppendTimeMs (spec default -1,
 *       unavailable) — dedicated V5 response methods, partition width 22 to 30 bytes.</li>
 *   <li>v6: unchanged in both directions (no field version range differs at v6+ vs v5;
 *       RecordErrors/ErrorMessage arrive in v8) — all four dispatches fall through to
 *       the v3/v5 methods.</li>
 *   <li>v7: unchanged in both directions (no field version range differs at v7+ vs v6)
 *       — all four dispatches fall through to the v3/v5 methods.</li>
 *   <li>v8: request unchanged vs v7 (falls through to the v3 methods); the response
 *       partition gains RecordErrors([]BatchIndexAndErrorMessage: BatchIndex int32 +
 *       BatchIndexErrorMessage string|null) and a trailing nullable ErrorMessage(string)
 *       after LogStartOffset (both ignorable) — dedicated response methods, partition
 *       width 30 + variable bytes.</li>
 *   <li>v9+ throw {@link CodecNotImplementedException}.</li>
 * </ul>
 */
class ProduceCodecTest {

    @Nested
    @DisplayName("Produce request (key 0)")
    class Request {

        @Test
        @DisplayName("v1 request is byte-identical to v0 (spec: v1 same as v0)")
        void v1RequestByteIdenticalToV0() {
            var req = new ProduceRequest(null, (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] v0 = ProduceCodec.encodeRequest((short) 0, req);
            byte[] v1 = ProduceCodec.encodeRequest((short) 1, req);

            assertArrayEquals(v0, v1, "v1 request must be byte-identical to v0");
            var decoded = ProduceCodec.decodeRequest((short) 1, ByteBuffer.wrap(v1));
            assertNull(decoded.transactionalId(), "v1 has no TransactionalId (added v3)");
            assertEquals((short) -1, decoded.acks());
            assertEquals(30000, decoded.timeoutMs());
            assertEquals("topic", decoded.topicData().getFirst().name());
        }

        @Test
        @DisplayName("v2 request is byte-identical to v0 (TransactionalId only arrives in v3)")
        void v2RequestByteIdenticalToV0() {
            var req = new ProduceRequest(null, (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] v0 = ProduceCodec.encodeRequest((short) 0, req);
            byte[] v2 = ProduceCodec.encodeRequest((short) 2, req);

            assertArrayEquals(v0, v2, "v2 request must be byte-identical to v0");
            var decoded = ProduceCodec.decodeRequest((short) 2, ByteBuffer.wrap(v2));
            assertNull(decoded.transactionalId(), "v2 has no TransactionalId (added v3)");
            assertEquals((short) -1, decoded.acks());
            assertEquals(30000, decoded.timeoutMs());
            assertEquals("topic", decoded.topicData().getFirst().name());
        }

        @Test
        @DisplayName("v1 request round-trips with null records")
        void v1RequestNullRecords() {
            var req = new ProduceRequest(null, (short) 1, 5000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(7, null)))));

            byte[] body = ProduceCodec.encodeRequest((short) 1, req);
            var decoded = ProduceCodec.decodeRequest((short) 1, ByteBuffer.wrap(body));
            assertNull(decoded.topicData().getFirst().partitionData().getFirst().records());
        }

        @Test
        @DisplayName("v3 request round-trips the leading nullable TransactionalId")
        void v3RoundTrip() {
            var req = new ProduceRequest("producer-1", (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] body = ProduceCodec.encodeRequest((short) 3, req);
            var decoded = ProduceCodec.decodeRequest((short) 3, ByteBuffer.wrap(body));

            assertEquals("producer-1", decoded.transactionalId());
            assertEquals((short) -1, decoded.acks());
            assertEquals(30000, decoded.timeoutMs());
            assertEquals("topic", decoded.topicData().getFirst().name());
            assertArrayEquals(new byte[]{1, 2, 3},
                    decoded.topicData().getFirst().partitionData().getFirst().records());
        }

        @Test
        @DisplayName("v3 null TransactionalId round-trips (written as length -1)")
        void v3NullTransactionalId() {
            var req = new ProduceRequest(null, (short) 1, 5000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(7, null)))));

            byte[] body = ProduceCodec.encodeRequest((short) 3, req);
            // v0 body was 25 bytes for this shape; v3 adds 2 (the -1 nullable-string length)
            assertEquals(27, body.length);
            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals((short) -1, buf.getShort(), "Transaction... null");
            assertEquals(25, buf.remaining(), "remainder must be exactly the v0 body");

            var decoded = ProduceCodec.decodeRequest((short) 3, ByteBuffer.wrap(body));
            assertNull(decoded.transactionalId());
            assertNull(decoded.topicData().getFirst().partitionData().getFirst().records());
        }

        @Test
        @DisplayName("v3 exact byte layout — TransactionalId(string) leads, then the v0 body")
        void v3ExactBytes() {
            var req = new ProduceRequest("txn", (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] body = ProduceCodec.encodeRequest((short) 3, req);
            byte[] v0Body = ProduceCodec.encodeRequest((short) 0, req);

            // v3 = v0 body (32) + leading nullable string (2 + 3) = 37
            assertEquals(37, body.length, "32 + (2+3)");

            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(3, buf.getShort(), "TransactionalId length");
            byte[] txn = new byte[3];
            buf.get(txn);
            assertEquals("txn", StandardCharsets.UTF_8.decode(ByteBuffer.wrap(txn)).toString(), "TransactionalId");
            assertEquals((short) -1, buf.getShort(), "Acks (after TransactionalId)");
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

            // The tail must be byte-identical to the v0 body (v3 only prepends).
            byte[] tail = new byte[v0Body.length];
            buf.position(0);
            System.arraycopy(body, 5, tail, 0, v0Body.length);
            assertArrayEquals(v0Body, tail, "v3 body after the leading field must equal the v0 body");
        }

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

        @Test
        @DisplayName("v1 response round-trips the trailing ThrottleTimeMs (LogAppendTimeMs still absent)")
        void v1RoundTrip() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 42L, 999L)))), 250);

            byte[] body = ProduceCodec.encodeResponse((short) 1, resp);
            var decoded = ProduceCodec.decodeResponse((short) 1, ByteBuffer.wrap(body));

            assertEquals(1, decoded.responses().size());
            var pr = decoded.responses().getFirst().partitionResponses().getFirst();
            assertEquals(0, pr.partitionIndex());
            assertEquals(42L, pr.baseOffset());
            assertEquals(-1L, pr.logAppendTimeMs(), "v1 has no LogAppendTimeMs (added v2) — default -1");
            assertEquals(250, decoded.throttleTimeMs(), "v1 writes the carried ThrottleTimeMs");
        }

        @Test
        @DisplayName("v1 exact byte layout — v0 body + trailing ThrottleTimeMs(int32); no LogAppendTimeMs")
        void v1ExactBytes() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(3, (short) 5, 100L, 777L)))), 1234);

            byte[] body = ProduceCodec.encodeResponse((short) 1, resp);

            // topicCount(4) + name(2+5) + partCount(4) + (index 4 + error 2 + offset 8) + throttle(4) = 33
            assertEquals(33, body.length, "4+(2+5)+4+14+4");

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
            assertEquals(1234, buf.getInt(), "ThrottleTimeMs (trailing, after the array)");
            assertEquals(0, buf.remaining(), "no LogAppendTimeMs at v1");
        }

        @Test
        @DisplayName("v1 response across multiple topics carries one trailing throttle")
        void v1MultipleTopics() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t1", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 1L, 0L),
                            new ProduceResponse.PartitionResponse(1, (short) 3, -1L, 0L))),
                    new ProduceResponse.TopicResponse("t2", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 7L, 0L)))), 55);

            byte[] body = ProduceCodec.encodeResponse((short) 1, resp);
            var decoded = ProduceCodec.decodeResponse((short) 1, ByteBuffer.wrap(body));

            assertEquals(2, decoded.responses().size());
            assertEquals(2, decoded.responses().get(0).partitionResponses().size());
            assertEquals((short) 3, decoded.responses().get(0).partitionResponses().get(1).errorCode());
            assertEquals(7L, decoded.responses().get(1).partitionResponses().get(0).baseOffset());
            assertEquals(55, decoded.throttleTimeMs());
        }

        @Test
        @DisplayName("v2 response round-trips per-partition LogAppendTimeMs and the trailing ThrottleTimeMs")
        void v2RoundTrip() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 42L, 1711000000L),
                            new ProduceResponse.PartitionResponse(1, (short) 3, -1L, -1L)))), 250);

            byte[] body = ProduceCodec.encodeResponse((short) 2, resp);
            var decoded = ProduceCodec.decodeResponse((short) 2, ByteBuffer.wrap(body));

            assertEquals(1, decoded.responses().size());
            var partitions = decoded.responses().getFirst().partitionResponses();
            assertEquals(2, partitions.size());
            assertEquals(0, partitions.get(0).partitionIndex());
            assertEquals(42L, partitions.get(0).baseOffset());
            assertEquals(1711000000L, partitions.get(0).logAppendTimeMs(), "v2 writes the carried LogAppendTimeMs");
            assertEquals((short) 3, partitions.get(1).errorCode());
            assertEquals(-1L, partitions.get(1).baseOffset());
            assertEquals(-1L, partitions.get(1).logAppendTimeMs(), "spec default -1 round-trips verbatim");
            assertEquals(250, decoded.throttleTimeMs(), "trailing ThrottleTimeMs retained from v1");
        }

        @Test
        @DisplayName("v2 exact byte layout — partition (index,error,baseOffset,logAppendTime) + trailing ThrottleTimeMs")
        void v2ExactBytes() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(3, (short) 5, 100L, 999999L)))), 1234);

            byte[] body = ProduceCodec.encodeResponse((short) 2, resp);

            // topicCount(4) + name(2+5) + partCount(4) + (index 4 + error 2 + offset 8 + logAppend 8) + throttle(4) = 41
            assertEquals(41, body.length, "4+(2+5)+4+22+4");

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
            assertEquals(999999L, buf.getLong(), "LogAppendTimeMs (after BaseOffset, new in v2)");
            assertEquals(1234, buf.getInt(), "ThrottleTimeMs (trailing, after the array)");
            assertEquals(0, buf.remaining(), "body must be exactly consumed");
        }

        @Test
        @DisplayName("v2 response across multiple topics keeps one trailing throttle and per-partition logAppendTime")
        void v2MultipleTopics() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t1", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 1L, 111L),
                            new ProduceResponse.PartitionResponse(1, (short) 3, -1L, -1L))),
                    new ProduceResponse.TopicResponse("t2", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 7L, 222L)))), 55);

            byte[] body = ProduceCodec.encodeResponse((short) 2, resp);
            var decoded = ProduceCodec.decodeResponse((short) 2, ByteBuffer.wrap(body));

            assertEquals(2, decoded.responses().size());
            assertEquals(2, decoded.responses().get(0).partitionResponses().size());
            assertEquals(111L, decoded.responses().get(0).partitionResponses().get(0).logAppendTimeMs());
            assertEquals((short) 3, decoded.responses().get(0).partitionResponses().get(1).errorCode());
            assertEquals(222L, decoded.responses().get(1).partitionResponses().get(0).logAppendTimeMs());
            assertEquals(55, decoded.throttleTimeMs());
        }

        @Test
        @DisplayName("v3 response is byte-identical to v2 (LogStartOffset arrives in v5)")
        void v3ResponseByteIdenticalToV2() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t1", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 1L, 111L),
                            new ProduceResponse.PartitionResponse(1, (short) 3, -1L, -1L))),
                    new ProduceResponse.TopicResponse("t2", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 7L, 222L)))), 55);

            byte[] bodyV3 = ProduceCodec.encodeResponse((short) 3, resp);
            byte[] bodyV2 = ProduceCodec.encodeResponse((short) 2, resp);
            assertArrayEquals(bodyV2, bodyV3, "v3 response must be byte-identical to v2");
            // v2 layout: topicCount(4) + t1(2+2) + partCount(4) + 2x22 + t2(2+2) + partCount(4) + 22 + throttle(4) = 90
            assertEquals(90, bodyV3.length, "v2 layout unchanged at v3");
        }

        @Test
        @DisplayName("v4 request is byte-identical to v3 (unchanged version)")
        void v4RequestByteIdenticalToV3() {
            var req = new ProduceRequest("producer-1", (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] bodyV4 = ProduceCodec.encodeRequest((short) 4, req);
            byte[] bodyV3 = ProduceCodec.encodeRequest((short) 3, req);
            assertArrayEquals(bodyV3, bodyV4, "v4 request must be byte-identical to v3");
            // v3 layout: 32 v0 body + 5 leading nullable string (producer-1 = 10 chars → 2+10)
            assertEquals(44, bodyV4.length, "v3 layout unchanged at v4");
        }

        @Test
        @DisplayName("v4 request round-trips through the v3 methods (no dedicated path)")
        void v4RoundTrip() {
            var req = new ProduceRequest("producer-1", (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] body = ProduceCodec.encodeRequest((short) 4, req);
            var decoded = ProduceCodec.decodeRequest((short) 4, ByteBuffer.wrap(body));

            assertEquals("producer-1", decoded.transactionalId());
            assertEquals((short) -1, decoded.acks());
            assertEquals(30000, decoded.timeoutMs());
            assertEquals("topic", decoded.topicData().getFirst().name());
            assertArrayEquals(new byte[]{1, 2, 3},
                    decoded.topicData().getFirst().partitionData().getFirst().records());
        }

        @Test
        @DisplayName("v4 response is byte-identical to v3 (LogStartOffset arrives in v5)")
        void v4ResponseByteIdenticalToV3() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 42L, 1711000000L)))), 250);

            byte[] bodyV4 = ProduceCodec.encodeResponse((short) 4, resp);
            byte[] bodyV3 = ProduceCodec.encodeResponse((short) 3, resp);
            assertArrayEquals(bodyV3, bodyV4, "v4 response must be byte-identical to v3");
            // v2/v3 layout: topicCount(4) + (2+5) + partCount(4) + 22 + throttle(4) = 41
            assertEquals(41, bodyV4.length, "v2 layout unchanged at v4");
        }

        @Test
        @DisplayName("v5 request is byte-identical to v4 (request unchanged)")
        void v5RequestByteIdenticalToV4() {
            var req = new ProduceRequest("producer-1", (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] bodyV5 = ProduceCodec.encodeRequest((short) 5, req);
            byte[] bodyV4 = ProduceCodec.encodeRequest((short) 4, req);
            assertArrayEquals(bodyV4, bodyV5, "v5 request must be byte-identical to v4");
            // v3 layout: 32 v0 body + 5 leading nullable string (producer-1 = 10 chars → 2+10)
            assertEquals(44, bodyV5.length, "v3 layout unchanged at v5");
        }

        @Test
        @DisplayName("v5 response round-trips the per-partition LogStartOffset")
        void v5RoundTrip() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t1", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 1L, 111L, 999L),
                            new ProduceResponse.PartitionResponse(1, (short) 3, -1L, -1L, 0L)))),
                    120);

            byte[] body = ProduceCodec.encodeResponse((short) 5, resp);
            var decoded = ProduceCodec.decodeResponse((short) 5, ByteBuffer.wrap(body));

            var parts = decoded.responses().getFirst().partitionResponses();
            assertEquals(999L, parts.get(0).logStartOffset());
            assertEquals(0L, parts.get(1).logStartOffset());
            assertEquals(111L, parts.get(0).logAppendTimeMs());
            assertEquals((short) 3, parts.get(1).errorCode());
            assertEquals(120, decoded.throttleTimeMs());
        }

        @Test
        @DisplayName("v5 response exact byte layout — partition width 30, trailing throttle")
        void v5ExactBytes() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(3, (short) 5, 100L, 999L, 777L)))), 888);

            byte[] body = ProduceCodec.encodeResponse((short) 5, resp);

            // topicCount(4) + name(2+5) + partCount(4) + (index 4 + error 2 + offset 8 + append 8 + start 8) + throttle(4) = 49
            assertEquals(49, body.length, "4+(2+5)+4+30+4");

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
            assertEquals(999L, buf.getLong(), "LogAppendTimeMs");
            assertEquals(777L, buf.getLong(), "LogStartOffset (new in v5)");
            assertEquals(888, buf.getInt(), "ThrottleTimeMs");
            assertEquals(0, buf.remaining(), "no further fields at v5");
        }

        @Test
        @DisplayName("v5 response decodes a partition with logStartOffset -1 (spec default)")
        void v5ErrorPartitionWithDefaultLogStartOffset() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t", List.of(
                            new ProduceResponse.PartitionResponse(2, KafkaErrorsForTest.UNKNOWN_TOPIC, -1L, -1L)))), 0);

            byte[] body = ProduceCodec.encodeResponse((short) 5, resp);
            var decoded = ProduceCodec.decodeResponse((short) 5, ByteBuffer.wrap(body));

            var pr = decoded.responses().getFirst().partitionResponses().getFirst();
            assertEquals(KafkaErrorsForTest.UNKNOWN_TOPIC, pr.errorCode());
            assertEquals(-1L, pr.logStartOffset(), "spec default -1 round-trips");
        }

        @Test
        @DisplayName("v6 request is byte-identical to v5 (unchanged version)")
        void v6RequestByteIdenticalToV5() {
            var req = new ProduceRequest("producer-1", (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] bodyV6 = ProduceCodec.encodeRequest((short) 6, req);
            byte[] bodyV5 = ProduceCodec.encodeRequest((short) 5, req);
            assertArrayEquals(bodyV5, bodyV6, "v6 request must be byte-identical to v5");
            // v3 layout: 32 v0 body + 5 leading nullable string (producer-1 = 10 chars → 2+10)
            assertEquals(44, bodyV6.length, "v3 layout unchanged at v6");
        }

        @Test
        @DisplayName("v6 request round-trips through the v3 methods (no dedicated path)")
        void v6RoundTrip() {
            var req = new ProduceRequest("producer-6", (short) 1, 5000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(7, new byte[]{9})))));

            byte[] body = ProduceCodec.encodeRequest((short) 6, req);
            var decoded = ProduceCodec.decodeRequest((short) 6, ByteBuffer.wrap(body));

            assertEquals("producer-6", decoded.transactionalId());
            assertEquals((short) 1, decoded.acks());
            assertEquals(5000, decoded.timeoutMs());
            assertEquals("t", decoded.topicData().getFirst().name());
            assertEquals(7, decoded.topicData().getFirst().partitionData().getFirst().index());
            assertArrayEquals(new byte[]{9}, decoded.topicData().getFirst().partitionData().getFirst().records());
        }

        @Test
        @DisplayName("v6 response is byte-identical to v5 (RecordErrors arrive in v8)")
        void v6ResponseByteIdenticalToV5() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 42L, 111L, 999L)))), 250);

            byte[] bodyV6 = ProduceCodec.encodeResponse((short) 6, resp);
            byte[] bodyV5 = ProduceCodec.encodeResponse((short) 5, resp);
            assertArrayEquals(bodyV5, bodyV6, "v6 response must be byte-identical to v5");
            // v5 layout: topicCount(4) + (2+5) + partCount(4) + 30 + throttle(4) = 49
            assertEquals(49, bodyV6.length, "v5 layout unchanged at v6");
        }

        @Test
        @DisplayName("v7 request is byte-identical to v6 (unchanged version)")
        void v7RequestByteIdenticalToV6() {
            var req = new ProduceRequest("producer-7", (short) -1, 30000,
                    List.of(new ProduceRequest.TopicData("topic", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))));

            byte[] bodyV7 = ProduceCodec.encodeRequest((short) 7, req);
            byte[] bodyV6 = ProduceCodec.encodeRequest((short) 6, req);
            assertArrayEquals(bodyV6, bodyV7, "v7 request must be byte-identical to v6");
        }

        @Test
        @DisplayName("v7 request round-trips through the v3 methods (no dedicated path)")
        void v7RoundTrip() {
            var req = new ProduceRequest("producer-7", (short) 1, 5000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(7, new byte[]{9})))));

            byte[] body = ProduceCodec.encodeRequest((short) 7, req);
            var decoded = ProduceCodec.decodeRequest((short) 7, ByteBuffer.wrap(body));

            assertEquals("producer-7", decoded.transactionalId());
            assertEquals((short) 1, decoded.acks());
            assertEquals(5000, decoded.timeoutMs());
            assertEquals("t", decoded.topicData().getFirst().name());
            assertEquals(7, decoded.topicData().getFirst().partitionData().getFirst().index());
            assertArrayEquals(new byte[]{9}, decoded.topicData().getFirst().partitionData().getFirst().records());
        }

        @Test
        @DisplayName("v7 response is byte-identical to v6 (RecordErrors arrive in v8)")
        void v7ResponseByteIdenticalToV6() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 42L, 111L, 999L)))), 250);

            byte[] bodyV7 = ProduceCodec.encodeResponse((short) 7, resp);
            byte[] bodyV6 = ProduceCodec.encodeResponse((short) 6, resp);
            assertArrayEquals(bodyV6, bodyV7, "v7 response must be byte-identical to v6");
            assertEquals(49, bodyV7.length, "v5/v6 layout unchanged at v7");
        }
    }

    @Nested
    @DisplayName("v8 request")
    class RequestV8 {

        @Test
        @DisplayName("v8 request is byte-identical to v7 (no request field differs at v8+)")
        void v8RequestByteIdenticalToV7() {
            var req = new ProduceRequest("txn-1", (short) -1, 5000,
                    List.of(new ProduceRequest.TopicData("t8",
                            List.of(new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3}),
                                    new ProduceRequest.PartitionData(1, new byte[0])))));

            byte[] bodyV8 = ProduceCodec.encodeRequest((short) 8, req);
            byte[] bodyV7 = ProduceCodec.encodeRequest((short) 7, req);
            assertArrayEquals(bodyV7, bodyV8, "v8 request must be byte-identical to v7");
        }

        @Test
        @DisplayName("v8 request round-trips through the v3 methods (no dedicated path)")
        void v8RequestRoundTrip() {
            var req = new ProduceRequest("txn-8", (short) -1, 60000,
                    List.of(new ProduceRequest.TopicData("tx8",
                            List.of(new ProduceRequest.PartitionData(2, new byte[]{9, 8, 7, 6})))));

            byte[] body = ProduceCodec.encodeRequest((short) 8, req);
            var decoded = ProduceCodec.decodeRequest((short) 8, ByteBuffer.wrap(body));

            assertEquals("txn-8", decoded.transactionalId());
            assertEquals((short) -1, decoded.acks());
            assertEquals(60000, decoded.timeoutMs());
            assertEquals("tx8", decoded.topicData().getFirst().name());
            assertEquals(2, decoded.topicData().getFirst().partitionData().getFirst().index());
            assertArrayEquals(new byte[]{9, 8, 7, 6},
                    decoded.topicData().getFirst().partitionData().getFirst().records());
        }
    }

    @Nested
    @DisplayName("v8 response")
    class ResponseV8 {

        @Test
        @DisplayName("v8 response round-trips RecordErrors and ErrorMessage")
        void v8RoundTrip() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("topic", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 42L, 111L, 999L,
                                    List.of(new ProduceResponse.PartitionResponse.BatchIndexAndErrorMessage(
                                            7, "corrupt batch"),
                                            new ProduceResponse.PartitionResponse.BatchIndexAndErrorMessage(
                                                    9, null)),
                                    "summary message")))), 30);

            byte[] body = ProduceCodec.encodeResponse((short) 8, resp);
            var decoded = ProduceCodec.decodeResponse((short) 8, ByteBuffer.wrap(body));

            var pr = decoded.responses().getFirst().partitionResponses().getFirst();
            assertEquals(42L, pr.baseOffset());
            assertEquals(111L, pr.logAppendTimeMs());
            assertEquals(999L, pr.logStartOffset());
            assertEquals(2, pr.recordErrors().size());
            assertEquals(7, pr.recordErrors().get(0).batchIndex());
            assertEquals("corrupt batch", pr.recordErrors().get(0).batchIndexErrorMessage());
            assertEquals(9, pr.recordErrors().get(1).batchIndex());
            assertNull(pr.recordErrors().get(1).batchIndexErrorMessage());
            assertEquals("summary message", pr.errorMessage());
            assertEquals(30, decoded.throttleTimeMs());
        }

        @Test
        @DisplayName("v8 response has the exact expected wire layout")
        void v8ExactBytes() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 1L, 0L, -1L,
                                    List.of(new ProduceResponse.PartitionResponse.BatchIndexAndErrorMessage(
                                            3, "bad")),
                                    "sum")))), 0);

            byte[] body = ProduceCodec.encodeResponse((short) 8, resp);
            // topicCount(4) + t(2+1) + partCount(4) + 30 + recordErrors count(4)
            // + entry batchIndex(4) + "bad"(2+3) + "sum"(2+3) + throttle(4) = 63
            assertEquals(63, body.length, "exact v8 wire layout");
            var decoded = ProduceCodec.decodeResponse((short) 8, ByteBuffer.wrap(body));
            assertEquals("bad", decoded.responses().getFirst().partitionResponses()
                    .getFirst().recordErrors().getFirst().batchIndexErrorMessage());
        }

        @Test
        @DisplayName("v8 response round-trips null recordErrors/errorMessage as empty array / null")
        void v8NullErrorsRoundTrip() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 0L, 0L)))), 0);
            // 5-arg compat constructor: logStartOffset -1, recordErrors null, errorMessage null.

            byte[] body = ProduceCodec.encodeResponse((short) 8, resp);
            // 30 + recordErrors count(4) + errorMessage len(2) + 0 = 36 per partition.
            var decoded = ProduceCodec.decodeResponse((short) 8, ByteBuffer.wrap(body));

            var pr = decoded.responses().getFirst().partitionResponses().getFirst();
            assertTrue(pr.recordErrors().isEmpty(), "null recordErrors decodes as an empty array");
            assertNull(pr.errorMessage());
        }
    }

    @Nested
    @DisplayName("Version dispatch")
    class Dispatch {

        @Test
        @DisplayName("v9 request encode throws CodecNotImplementedException (next unimplemented)")
        void v9RequestEncodeNotImplemented() {
            var req = new ProduceRequest(null, (short) 1, 1000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1})))));
            assertThrows(CodecNotImplementedException.class,
                    () -> ProduceCodec.encodeRequest((short) 9, req));
        }

        @Test
        @DisplayName("v9 request decode throws CodecNotImplementedException")
        void v9RequestDecodeNotImplemented() {
            var req = new ProduceRequest(null, (short) 1, 1000,
                    List.of(new ProduceRequest.TopicData("t", List.of(
                            new ProduceRequest.PartitionData(0, new byte[]{1})))));
            byte[] body = ProduceCodec.encodeRequest((short) 0, req);
            assertThrows(CodecNotImplementedException.class,
                    () -> ProduceCodec.decodeRequest((short) 9, ByteBuffer.wrap(body)));
        }

        @Test
        @DisplayName("v9 response encode throws CodecNotImplementedException")
        void v9ResponseEncodeNotImplemented() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 0L, 0L)))), 0);
            assertThrows(CodecNotImplementedException.class,
                    () -> ProduceCodec.encodeResponse((short) 9, resp));
        }

        @Test
        @DisplayName("v9 response decode throws CodecNotImplementedException")
        void v9ResponseDecodeNotImplemented() {
            var resp = new ProduceResponse(List.of(
                    new ProduceResponse.TopicResponse("t", List.of(
                            new ProduceResponse.PartitionResponse(0, (short) 0, 0L, 0L)))), 0);
            byte[] body = ProduceCodec.encodeResponse((short) 0, resp);
            assertThrows(CodecNotImplementedException.class,
                    () -> ProduceCodec.decodeResponse((short) 9, ByteBuffer.wrap(body)));
        }
    }

    /** Local alias for a Kafka error code used by the response tests (avoids broker-package import). */
    private static final class KafkaErrorsForTest {
        static final short UNKNOWN_TOPIC = 3;
    }
}
