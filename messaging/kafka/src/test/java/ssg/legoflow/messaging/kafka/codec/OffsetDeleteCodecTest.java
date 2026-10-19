package ssg.legoflow.messaging.kafka.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.OffsetDeleteRequest;
import ssg.legoflow.messaging.kafka.protocol.OffsetDeleteResponse;

/**
 * Golden-vector and round-trip tests for {@link OffsetDeleteCodec} (API key 47).
 *
 * <p><b>Spec:</b> {@code doc/spec/message/OffsetDeleteRequest.json} /
 * {@code OffsetDeleteResponse.json} — both {@code validVersions: "0"},
 * {@code flexibleVersions: "none"}: a single fixed-width v0 wire format.
 * <ul>
 *   <li>Request: {@code GroupId(string)} + {@code Topics[]} of
 *       {@code Name(string)} + {@code Partitions[]} of {@code PartitionIndex(int32)}.</li>
 *   <li>Response: {@code ErrorCode(int16)} + {@code ThrottleTimeMs(int32, ignorable)}
 *       + {@code Topics[]} of {@code Name(string)} + {@code Partitions[]} of
 *       {@code PartitionIndex(int32)} + {@code ErrorCode(int16)}.</li>
 * </ul>
 *
 * <p>The in-house response model has no {@code ThrottleTimeMs} field, so the codec
 * writes it as 0 on encode and reads + discards it on decode (models unchanged).
 */
@DisplayName("OffsetDeleteCodec v0")
class OffsetDeleteCodecTest {

    private static final OffsetDeleteRequest REQUEST = new OffsetDeleteRequest("group1", List.of(
            new OffsetDeleteRequest.TopicData("topic",
                    List.of(new OffsetDeleteRequest.PartitionData(0),
                            new OffsetDeleteRequest.PartitionData(1)))));

    private static final OffsetDeleteResponse RESPONSE = new OffsetDeleteResponse((short) 0, List.of(
            new OffsetDeleteResponse.TopicData("topic",
                    List.of(new OffsetDeleteResponse.PartitionData(0, (short) 0),
                            new OffsetDeleteResponse.PartitionData(1, (short) 0)))));

    // ---------------- golden vectors ----------------
    // Independently verified byte-for-byte against a Python reference encoder
    // built from the spec JSON (doc/spec/message/OffsetDeleteRequest.json etc.).

    /** Request v0: GroupId "group1", 1 topic "topic" with partitions 0, 1. */
    private static final byte[] REQ_V0_GOLDEN = fromHex(
            "000667726f757031" +        // GroupId "group1"
                    "00000001" +        // Topics count = 1 (int32)
                    "0005746f706963" +  // Name "topic"
                    "00000002" +        // Partitions count = 2 (int32)
                    "00000000" +        // PartitionIndex = 0 (int32)
                    "00000001");        // PartitionIndex = 1 (int32)

    /** Response v0: ErrorCode 0, ThrottleTimeMs 0, 1 topic "topic", partitions 0, 1. */
    private static final byte[] RSP_V0_GOLDEN = fromHex(
            "0000" +                    // ErrorCode = 0 (int16)
                    "00000000" +        // ThrottleTimeMs = 0 (int32, ignorable)
                    "00000001" +        // Topics count = 1 (int32)
                    "0005746f706963" +  // Name "topic"
                    "00000002" +        // Partitions count = 2 (int32)
                    "00000000" +        // PartitionIndex = 0 (int32)
                    "0000" +            //   ErrorCode = 0 (int16)
                    "00000001" +        // PartitionIndex = 1 (int32)
                    "0000");            //   ErrorCode = 0 (int16)

    // ---------------- request ----------------

    @Nested
    @DisplayName("Request")
    class Request {

        @Test
        @DisplayName("round-trip")
        void roundTrip() {
            byte[] encoded = OffsetDeleteCodec.encodeRequest(OffsetDeleteCodec.MIN_VERSION, REQUEST);
            OffsetDeleteRequest decoded =
                    OffsetDeleteCodec.decodeRequest(OffsetDeleteCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
            assertThat(decoded).isEqualTo(REQUEST);
        }

        @Test
        @DisplayName("v0 golden")
        void v0Golden() {
            assertThat(OffsetDeleteCodec.encodeRequest(OffsetDeleteCodec.MIN_VERSION, REQUEST))
                    .isEqualTo(REQ_V0_GOLDEN);
        }

        @Test
        @DisplayName("empty request round-trips")
        void emptyRoundTrip() {
            OffsetDeleteRequest req = new OffsetDeleteRequest("g", List.of());
            byte[] encoded = OffsetDeleteCodec.encodeRequest(OffsetDeleteCodec.MIN_VERSION, req);
            OffsetDeleteRequest decoded =
                    OffsetDeleteCodec.decodeRequest(OffsetDeleteCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
            assertThat(decoded).isEqualTo(req);
        }

        @Test
        @DisplayName("multiple topics round-trip")
        void multipleTopics() {
            OffsetDeleteRequest req = new OffsetDeleteRequest("g", List.of(
                    new OffsetDeleteRequest.TopicData("t1",
                            List.of(new OffsetDeleteRequest.PartitionData(3))),
                    new OffsetDeleteRequest.TopicData("t2",
                            List.of(new OffsetDeleteRequest.PartitionData(0),
                                    new OffsetDeleteRequest.PartitionData(1),
                                    new OffsetDeleteRequest.PartitionData(2)))));
            byte[] encoded = OffsetDeleteCodec.encodeRequest(OffsetDeleteCodec.MIN_VERSION, req);
            OffsetDeleteRequest decoded =
                    OffsetDeleteCodec.decodeRequest(OffsetDeleteCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
            assertThat(decoded).isEqualTo(req);
        }
    }

    // ---------------- response ----------------

    @Nested
    @DisplayName("Response")
    class Response {

        @Test
        @DisplayName("round-trip")
        void roundTrip() {
            byte[] encoded = OffsetDeleteCodec.encodeResponse(OffsetDeleteCodec.MIN_VERSION, RESPONSE);
            OffsetDeleteResponse decoded =
                    OffsetDeleteCodec.decodeResponse(OffsetDeleteCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
            assertThat(decoded).isEqualTo(RESPONSE);
        }

        @Test
        @DisplayName("v0 golden")
        void v0Golden() {
            assertThat(OffsetDeleteCodec.encodeResponse(OffsetDeleteCodec.MIN_VERSION, RESPONSE))
                    .isEqualTo(RSP_V0_GOLDEN);
        }

        @Test
        @DisplayName("ThrottleTimeMs is written 0 and read + discarded")
        void throttleTimeDiscarded() {
            OffsetDeleteResponse resp = new OffsetDeleteResponse((short) 7, List.of(
                    new OffsetDeleteResponse.TopicData("t",
                            List.of(new OffsetDeleteResponse.PartitionData(4, (short) 5)))));
            byte[] encoded = OffsetDeleteCodec.encodeResponse(OffsetDeleteCodec.MIN_VERSION, resp);
            // ThrottleTimeMs sits at offset 2 (after the int16 ErrorCode); must be 0.
            ByteBuffer bb = ByteBuffer.wrap(encoded);
            bb.getShort(); // ErrorCode
            int throttle = bb.getInt(); // ThrottleTimeMs
            assertThat(throttle).isZero();
            OffsetDeleteResponse decoded =
                    OffsetDeleteCodec.decodeResponse(OffsetDeleteCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
            assertThat(decoded.errorCode()).isEqualTo((short) 7);
            assertThat(decoded.topics()).hasSize(1);
            assertThat(decoded.topics().getFirst().name()).isEqualTo("t");
            assertThat(decoded.topics().getFirst().partitions())
                    .containsExactly(new OffsetDeleteResponse.PartitionData(4, (short) 5));
        }
    }

    // ---------------- version gating ----------------

    @Nested
    @DisplayName("Version")
    class Version {

        @Test
        @DisplayName("request rejects versions outside range 0")
        void requestRejectsOutOfRange() {
            for (short v = 1; v <= 5; v++) {
                final short vv = v;
                assertThatThrownBy(() -> OffsetDeleteCodec.encodeRequest(vv, REQUEST))
                        .isInstanceOf(CodecNotImplementedException.class);
                assertThatThrownBy(() -> OffsetDeleteCodec.decodeRequest(vv, ByteBuffer.allocate(0)))
                        .isInstanceOf(CodecNotImplementedException.class);
            }
        }

        @Test
        @DisplayName("response rejects versions outside range 0")
        void responseRejectsOutOfRange() {
            for (short v = 1; v <= 5; v++) {
                final short vv = v;
                assertThatThrownBy(() -> OffsetDeleteCodec.encodeResponse(vv, RESPONSE))
                        .isInstanceOf(CodecNotImplementedException.class);
                assertThatThrownBy(() -> OffsetDeleteCodec.decodeResponse(vv, ByteBuffer.allocate(0)))
                        .isInstanceOf(CodecNotImplementedException.class);
            }
        }

        @Test
        @DisplayName("facade MIN_VERSION == MAX_VERSION == 0")
        void versionConstants() {
            assertThat(OffsetDeleteCodec.MIN_VERSION).isZero();
            assertThat(OffsetDeleteCodec.MAX_VERSION).isZero();
        }
    }

    // ---------------- helpers ----------------

    private static byte[] fromHex(String hex) {
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}
