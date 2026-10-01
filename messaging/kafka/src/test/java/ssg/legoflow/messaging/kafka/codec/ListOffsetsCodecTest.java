package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.ListOffsetsRequest;
import ssg.legoflow.messaging.kafka.protocol.ListOffsetsResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link ListOffsetsCodec} — Kafka ListOffsets request/response (API key 2).
 *
 * <p>Covers the pinned version (v1) and the full implemented range (v0–v8). The in-house
 * models are compact (v1 shape), so version-gated fields the model does not expose
 * ({@code ReplicaId}, {@code IsolationLevel}, {@code CurrentLeaderEpoch},
 * {@code MaxNumOffsets}, {@code ThrottleTimeMs}, {@code LeaderEpoch}) are written with a
 * neutral spec default and read + discarded on decode; the round-trip therefore preserves
 * only the fields the model carries ({@code name}, {@code partitionIndex}, {@code timestamp},
 * {@code errorCode}, {@code offset}).
 *
 * <p>Byte-layout assertions are verified byte-for-byte against the canonical Kafka
 * ListOffsetsRequest.json / ListOffsetsResponse.json spec (see
 * {@code doc/CODEC_VALIDATION_ListOffsets.md}).
 */
@DisplayName("ListOffsetsCodec (API key 2)")
class ListOffsetsCodecTest {

    // ── Fixture builders ────────────────────────────────────────────────

    private static ListOffsetsRequest req(String topic, int part, long ts) {
        return new ListOffsetsRequest(List.of(
                new ListOffsetsRequest.TopicOffsets(topic, List.of(
                        new ListOffsetsRequest.PartitionOffsets(part, ts)))));
    }

    private static ListOffsetsResponse resp(String topic, int part, short err, long ts, long off) {
        return new ListOffsetsResponse(List.of(
                new ListOffsetsResponse.TopicResponse(topic, List.of(
                        new ListOffsetsResponse.PartitionResponse(part, err, ts, off)))));
    }

    private static ListOffsetsRequest roundTripReq(short v, ListOffsetsRequest r) {
        byte[] b = ListOffsetsCodec.encodeRequest(v, r);
        return ListOffsetsCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static ListOffsetsResponse roundTripResp(short v, ListOffsetsResponse r) {
        byte[] b = ListOffsetsCodec.encodeResponse(v, r);
        return ListOffsetsCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Pinned version ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Pinned version (v1)")
    class Pinned {

        @Test
        @DisplayName("PINNED_VERSION is v1 (client/broker wire format)")
        void pinnedVersion() {
            assertEquals((short) 1, ListOffsetsCodec.PINNED_VERSION);
        }

        @Test
        @DisplayName("single-arg encodeRequest pins to v1")
        void pinnedEncodeRequest() {
            byte[] pinned = ListOffsetsCodec.encodeRequest((short) 1, req("t", 0, -1L));
            byte[] oneArg = ListOffsetsCodec.encodeRequest(req("t", 0, -1L));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("request round-trip (v1) preserves name/partitionIndex/timestamp")
        void requestRoundTrip() {
            ListOffsetsRequest decoded = roundTripReq((short) 1, req("orders", 7, -2L));
            assertEquals(1, decoded.topics().size());
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(1, decoded.topics().get(0).partitions().size());
            assertEquals(7, decoded.topics().get(0).partitions().get(0).partitionIndex());
            assertEquals(-2L, decoded.topics().get(0).partitions().get(0).timestamp());
        }

        @Test
        @DisplayName("response round-trip (v1) preserves name/partitionIndex/errorCode/timestamp/offset")
        void responseRoundTrip() {
            ListOffsetsResponse decoded = roundTripResp((short) 1, resp("orders", 7, (short) 0, 1000L, 42L));
            assertEquals("orders", decoded.topics().get(0).name());
            var p = decoded.topics().get(0).partitions().get(0);
            assertEquals(7, p.partitionIndex());
            assertEquals(0, p.errorCode());
            assertEquals(1000L, p.timestamp());
            assertEquals(42L, p.offset());
        }
    }

    // ── Request byte layout ─────────────────────────────────────────────

    @Nested
    @DisplayName("Request wire format")
    class RequestLayout {

        @Test
        @DisplayName("v1 = int32 ReplicaId(-1) + int32 numTopics(1) + name + int32 numPartitions(1) + int32 PartitionIndex + int64 Timestamp")
        void v1() {
            byte[] b = ListOffsetsCodec.encodeRequest((short) 1, req("t", 0, -1L));
            assertEquals(27, b.length); // 4+4+2+1+4+4+8
            assertArrayEquals(b, new byte[]{
                    -1, -1, -1, -1,         // ReplicaId = -1
                    0, 0, 0, 1,             // numTopics = 1
                    0, 1,                   // string length = 1
                    't',                    // name
                    0, 0, 0, 1,             // numPartitions = 1
                    0, 0, 0, 0,             // PartitionIndex = 0
                    -1, -1, -1, -1, -1, -1, -1, -1   // Timestamp = -1
            });
        }

        @Test
        @DisplayName("v0 adds int32 MaxNumOffsets(1) at the end of the partition block")
        void v0MaxNumOffsets() {
            byte[] b = ListOffsetsCodec.encodeRequest((short) 0, req("t", 0, -1L));
            // v1 (27) + int32 MaxNumOffsets = 31. Partition order at v0:
            // PartitionIndex, Timestamp, MaxNumOffsets (no CurrentLeaderEpoch below v4).
            assertEquals(31, b.length);
            // MaxNumOffsets is the trailing int32 (offset 27..31): big-endian 1 = 00 00 00 01.
            assertArrayEquals(new byte[]{0, 0, 0, 1}, java.util.Arrays.copyOfRange(b, 27, 31));
        }

        @Test
        @DisplayName("v2 adds byte IsolationLevel(-1) after the 4-byte ReplicaId")
        void v2IsolationLevel() {
            byte[] b = ListOffsetsCodec.encodeRequest((short) 2, req("t", 0, -1L));
            assertEquals(28, b.length); // v1 (27) + 1 byte
            assertEquals((byte) -1, b[4]); // IsolationLevel immediately after the 4-byte ReplicaId
            // numTopics now starts at offset 5 (a fixed int32, no varint in pre-flexible):
            assertEquals(1, ByteBuffer.wrap(b, 5, 4).getInt());
        }

        @Test
        @DisplayName("v4 adds int32 CurrentLeaderEpoch(-1) before Timestamp in the partition block")
        void v4CurrentLeaderEpoch() {
            byte[] b = ListOffsetsCodec.encodeRequest((short) 4, req("t", 0, -1L));
            // v1 (27) + IsolationLevel(1, v2+) + int32 CurrentLeaderEpoch = 32.
            assertEquals(32, b.length);
            // Head: ReplicaId(0-3) | IsolationLevel(4) | numTopics(5-8) | name(9-11)
            //       | numPartitions(12-15) | PartitionIndex(16-19)
            //       | CurrentLeaderEpoch(20-23) | Timestamp(24-31).
            ByteBuffer w = ByteBuffer.wrap(b);
            w.position(20);
            assertEquals(-1, w.getInt());   // CurrentLeaderEpoch
            assertEquals(-1L, w.getLong()); // Timestamp
        }

        @Test
        @DisplayName("numPartitions counts every partition in the topic")
        void numPartitions() {
            var req = new ListOffsetsRequest(List.of(
                    new ListOffsetsRequest.TopicOffsets("t", List.of(
                            new ListOffsetsRequest.PartitionOffsets(0, -1L),
                            new ListOffsetsRequest.PartitionOffsets(1, -2L)))));
            byte[] b = ListOffsetsCodec.encodeRequest((short) 1, req);
            // single-partition v1 (27) + a 2nd partition (int32 PartitionIndex + int64 Timestamp = 12) = 39
            assertEquals(27 + 12, b.length);
            // numPartitions is the int32 right after the 1-byte name (offset 11):
            assertEquals(2, ByteBuffer.wrap(b, 11, 4).getInt());
            ListOffsetsRequest d = ListOffsetsCodec.decodeRequest((short) 1, ByteBuffer.wrap(b));
            assertEquals(2, d.topics().get(0).partitions().size());
        }
    }

    // ── Response byte layout ────────────────────────────────────────────

    @Nested
    @DisplayName("Response wire format")
    class ResponseLayout {

        @Test
        @DisplayName("v1 = int32 numTopics(1) + name + int32 numPartitions(1) + int32 PartitionIndex + int16 ErrorCode + int64 Timestamp + int64 Offset")
        void v1() {
            byte[] b = ListOffsetsCodec.encodeResponse((short) 1, resp("t", 0, (short) 0, 100L, 42L));
            assertEquals(33, b.length); // 4+2+1+4+4+2+8+8
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 1,             // numTopics = 1
                    0, 1,                   // string length = 1
                    't',                    // name
                    0, 0, 0, 1,             // numPartitions = 1
                    0, 0, 0, 0,             // PartitionIndex = 0
                    0, 0,                   // ErrorCode = 0
                    0, 0, 0, 0, 0, 0, 0, 100,   // Timestamp = 100
                    0, 0, 0, 0, 0, 0, 0, 42      // Offset = 42
            });
        }

        @Test
        @DisplayName("v2 adds int32 ThrottleTimeMs(0) at the front")
        void v2ThrottleTime() {
            byte[] b = ListOffsetsCodec.encodeResponse((short) 2, resp("t", 0, (short) 0, 100L, 42L));
            assertEquals(37, b.length); // v1 (33) + int32 ThrottleTimeMs
            assertArrayEquals(new byte[]{0, 0, 0, 0}, java.util.Arrays.copyOfRange(b, 0, 4));
        }

        @Test
        @DisplayName("v4 adds int32 LeaderEpoch(-1) at the end of the partition block")
        void v4LeaderEpoch() {
            byte[] b = ListOffsetsCodec.encodeResponse((short) 4, resp("t", 0, (short) 0, 100L, 42L));
            // v1 (33) + ThrottleTimeMs(4, v2+) + int32 LeaderEpoch = 41.
            assertEquals(41, b.length);
            // LeaderEpoch is the trailing int32 of the body (offset 37..41):
            assertArrayEquals(new byte[]{-1, -1, -1, -1}, java.util.Arrays.copyOfRange(b, 37, 41));
        }

        @Test
        @DisplayName("v0 uses OldStyleOffsets(int64[]) instead of Timestamp+Offset; offset taken from element 0")
        void v0OldStyleOffsets() {
            byte[] b = ListOffsetsCodec.encodeResponse((short) 0, resp("t", 0, (short) 0, 100L, 42L));
            // numTopics(4)+name(2+1)+numPartitions(4)+PartitionIndex(4)+ErrorCode(2)
            //   + int32 count(4) + int64 OldStyleOffsets[0](8) = 29.
            assertEquals(29, b.length);
            // int32 count(1) sits at offset 17 (after PartitionIndex+ErrorCode); the
            // int64 offset(42) follows at 21.
            ByteBuffer w = ByteBuffer.wrap(b);
            w.position(17);
            assertEquals(1, w.getInt());       // OldStyleOffsets count = 1
            assertEquals(42L, w.getLong());    // OldStyleOffsets[0] = 42 (from the offset field)
            // Decode reconstructs the offset from element 0 and defaults timestamp to 0.
            ListOffsetsResponse d = ListOffsetsCodec.decodeResponse((short) 0, ByteBuffer.wrap(b));
            assertEquals(42L, d.topics().get(0).partitions().get(0).offset());
            assertEquals(0L, d.topics().get(0).partitions().get(0).timestamp());
        }
    }

    // ── Full range round-trips ──────────────────────────────────────────

    @Nested
    @DisplayName("Full version range round-trips (v0–v8)")
    class FullRange {

        @Test
        @DisplayName("every fixed version v0–v5 round-trips the request")
        void requestFixed() {
            for (short v = 0; v <= 5; v++) {
                ListOffsetsRequest d = roundTripReq(v, req("topic", 3, -1L));
                assertEquals("topic", d.topics().get(0).name(), "v" + v);
                assertEquals(3, d.topics().get(0).partitions().get(0).partitionIndex(), "v" + v);
                assertEquals(-1L, d.topics().get(0).partitions().get(0).timestamp(), "v" + v);
            }
        }

        @Test
        @DisplayName("every flexible version v6–v8 round-trips the request")
        void requestFlexible() {
            for (short v = 6; v <= 8; v++) {
                ListOffsetsRequest d = roundTripReq(v, req("topic", 3, -2L));
                assertEquals("topic", d.topics().get(0).name(), "v" + v);
                assertEquals(3, d.topics().get(0).partitions().get(0).partitionIndex(), "v" + v);
                assertEquals(-2L, d.topics().get(0).partitions().get(0).timestamp(), "v" + v);
            }
        }

        @Test
        @DisplayName("every version v0–v8 round-trips the response")
        void responseAll() {
            for (short v = 0; v <= 8; v++) {
                ListOffsetsResponse d = roundTripResp(v, resp("topic", 3, (short) 0, 500L, 77L));
                assertEquals("topic", d.topics().get(0).name(), "v" + v);
                var p = d.topics().get(0).partitions().get(0);
                assertEquals(3, p.partitionIndex(), "v" + v);
                assertEquals(0, p.errorCode(), "v" + v);
                assertEquals(77L, p.offset(), "v" + v);
            }
        }
    }

    // ── Flexible v6 byte layout ─────────────────────────────────────────

    @Nested
    @DisplayName("Flexible v6+ layout")
    class Flexible {

        @Test
        @DisplayName("v6 response = int32 ThrottleTime(0) + varint numTopics(1) + compactString name + varint numPartitions(1) + partition(int32,int16,int64,int64,int32) + 3 empty tagged-field varints")
        void v6Response() {
            byte[] b = ListOffsetsCodec.encodeResponse((short) 6, resp("t", 0, (short) 0, 100L, 42L));
            int expected =
                    4                                   // ThrottleTimeMs
                    + 1                                 // varint numTopics=1
                    + 1 + 1                             // compactString "t": varint(2) + 1 byte
                    + 1                                 // varint numPartitions=1
                    + 4 + 2 + 8 + 8 + 4                 // partition: idx+err+ts+off+leaderEpoch
                    + 1 + 1 + 1;                        // 3 empty end-of-tagged-fields varints
            assertEquals(37, b.length); // expected is 37
            // Head: ThrottleTime=0, numTopics varint=1, compactString length varint=2.
            assertArrayEquals(new byte[]{0, 0, 0, 0, 1, 2, 't'}, java.util.Arrays.copyOfRange(b, 0, 7));
        }

        @Test
        @DisplayName("v6 request = int32 ReplicaId(-1) + byte IsolationLevel(-1) + varint numTopics(1) + compactString name + varint numPartitions(1) + partition(int32,int32,int64) + 3 empty tagged-field varints")
        void v6Request() {
            byte[] b = ListOffsetsCodec.encodeRequest((short) 6, req("t", 0, -1L));
            int expected =
                    4                                   // ReplicaId
                    + 1                                 // IsolationLevel
                    + 1                                 // varint numTopics=1
                    + 1 + 1                             // compactString "t": varint(2) + 1 byte
                    + 1                                 // varint numPartitions=1
                    + 4 + 4 + 8                         // partition: idx+leaderEpoch+ts
                    + 1 + 1 + 1;                        // 3 empty end-of-tagged-fields varints
            assertEquals(28, b.length); // expected is 28
            // Head: ReplicaId=-1 (int32 = ff ff ff ff), IsolationLevel=-1, numTopics varint=1, compactString varint=2.
            assertArrayEquals(
                    new byte[]{-1, -1, -1, -1, -1, 1, 2, 't'},
                    java.util.Arrays.copyOfRange(b, 0, 8));
        }
    }

    // ── Version guard ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Version guard")
    class VersionGuard {

        @Test
        @DisplayName("request v9 throws CodecNotImplementedException (beyond spec max v8)")
        void requestV9() {
            assertThrows(CodecNotImplementedException.class,
                    () -> ListOffsetsCodec.encodeRequest((short) 9, req("t", 0, -1L)));
        }

        @Test
        @DisplayName("request negative version throws CodecNotImplementedException")
        void requestNegative() {
            assertThrows(CodecNotImplementedException.class,
                    () -> ListOffsetsCodec.encodeRequest((short) -1, req("t", 0, -1L)));
        }

        @Test
        @DisplayName("response v9 throws CodecNotImplementedException (beyond spec max v8)")
        void responseV9() {
            assertThrows(CodecNotImplementedException.class,
                    () -> ListOffsetsCodec.encodeResponse((short) 9, resp("t", 0, (short) 0, 100L, 42L)));
        }

        @Test
        @DisplayName("decodeRequest out-of-range version throws CodecNotImplementedException")
        void decodeRequestOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> ListOffsetsCodec.decodeRequest((short) 99, ByteBuffer.wrap(new byte[0])));
        }

        @Test
        @DisplayName("decodeResponse out-of-range version throws CodecNotImplementedException")
        void decodeResponseOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> ListOffsetsCodec.decodeResponse((short) 99, ByteBuffer.wrap(new byte[0])));
        }
    }
}
