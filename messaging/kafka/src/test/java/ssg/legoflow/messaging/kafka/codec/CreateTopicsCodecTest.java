package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.CreateTopicsRequest;
import ssg.legoflow.messaging.kafka.protocol.CreateTopicsResponse;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link CreateTopicsCodec} — Kafka CreateTopics request/response (API key 19).
 *
 * <p>Covers the pinned version (v0, the shape {@code KafkaAdminClient} sends) and the full
 * implemented range v0–v7. Byte-layout assertions in the flexible (KIP-482) versions verify
 * the compact-array count encoding against Apache Kafka 3.6.1 generated code
 * ({@code CreateTopicsRequestData} / {@code CreateTopicsResponseData}):
 * <ul>
 *   <li>compact array length varint = {@code size + 1} on write, {@code varint - 1} on read;</li>
 *   <li>the nullable response per-topic {@code Configs} array encodes {@code varint(0)} when
 *       null and {@code varint(size + 1)} otherwise;</li>
 *   <li>nullable compact strings encode {@code varint(0)} when null (not {@code varint(1)}).</li>
 * </ul>
 */
@DisplayName("CreateTopicsCodec (API key 19)")
class CreateTopicsCodecTest {

    // ── Fixture builders ────────────────────────────────────────────────

    private static Map<String, String> configs(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /**
     * One topic "orders", 3 partitions, RF 2, one assignment (partition 0 → brokers 1, 2),
     * two configs ("retention.ms"=604800000, "cleanup.policy"="delete").
     */
    private static CreateTopicsRequest req(int timeoutMs) {
        return new CreateTopicsRequest(List.of(new CreateTopicsRequest.TopicCreate(
                "orders", 3, (short) 2,
                List.of(new CreateTopicsRequest.TopicCreate.Assignment(0, List.of(1, 2))),
                configs("retention.ms", "604800000", "cleanup.policy", "delete"))),
                timeoutMs);
    }

    private static CreateTopicsResponse resp(String name, short err) {
        return new CreateTopicsResponse(List.of(new CreateTopicsResponse.TopicResult(name, err)));
    }

    private static CreateTopicsRequest roundTripReq(short v, CreateTopicsRequest r) {
        byte[] b = CreateTopicsCodec.encodeRequest(v, r);
        return CreateTopicsCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static CreateTopicsResponse roundTripResp(short v, CreateTopicsResponse r) {
        byte[] b = CreateTopicsCodec.encodeResponse(v, r);
        return CreateTopicsCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Pinned version ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Pinned version (v0)")
    class Pinned {

        @Test
        @DisplayName("PINNED_VERSION is v0 (KafkaAdminClient wire format)")
        void pinnedVersion() {
            assertEquals((short) 0, CreateTopicsCodec.PINNED_VERSION);
        }

        @Test
        @DisplayName("single-arg encodeRequest pins to v0")
        void pinnedEncodeRequest() {
            byte[] pinned = CreateTopicsCodec.encodeRequest((short) 0, req(30000));
            byte[] oneArg = CreateTopicsCodec.encodeRequest(req(30000));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("single-arg encodeResponse pins to v0")
        void pinnedEncodeResponse() {
            byte[] pinned = CreateTopicsCodec.encodeResponse((short) 0, resp("orders", (short) 0));
            byte[] oneArg = CreateTopicsCodec.encodeResponse(resp("orders", (short) 0));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("request round-trip (v0) preserves name/partitions/replication/assignments/configs/timeout")
        void requestRoundTrip() {
            CreateTopicsRequest decoded = roundTripReq((short) 0, req(30000));
            assertEquals(1, decoded.topics().size());
            CreateTopicsRequest.TopicCreate t = decoded.topics().get(0);
            assertEquals("orders", t.name());
            assertEquals(3, t.numPartitions());
            assertEquals((short) 2, t.replicationFactor());
            assertEquals(1, t.assignments().size());
            assertEquals(0, t.assignments().get(0).partitionIndex());
            assertEquals(List.of(1, 2), t.assignments().get(0).brokerIds());
            assertEquals(configs("retention.ms", "604800000", "cleanup.policy", "delete"), t.configs());
            assertEquals(30000, decoded.timeoutMs());
        }

        @Test
        @DisplayName("response round-trip (v0) preserves name/errorCode")
        void responseRoundTrip() {
            CreateTopicsResponse decoded = roundTripResp((short) 0, resp("orders", (short) 0));
            assertEquals(1, decoded.topics().size());
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(0, decoded.topics().get(0).errorCode());
        }
    }

    // ── Request byte layout ─────────────────────────────────────────────

    @Nested
    @DisplayName("Request wire format")
    class RequestLayout {

        @Test
        @DisplayName("v0 = int32 numTopics + [name, int32 partitions, int16 rf, int32 nAssign, [int32 part, int32 nBrokers, int32*brokers], int32 nConfigs, [name, nullableString value]] + int32 timeout")
        void v0() {
            byte[] b = CreateTopicsCodec.encodeRequest((short) 0, req(30000));
            // 4 numTopics + (2+6) name + 4 partitions + 2 rf + 4 assignCount + (4+4+8) partitionIndex/brokerIds
            // + 4 configCount + (2+12)+(2+9) cfg1 + (2+14)+(2+6) cfg2 + 4 timeoutMs = 95
            assertEquals(4 + 8 + 4 + 2 + 4 + 16 + 4 + 25 + 24 + 4, b.length); // = 95
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 1,               // numTopics = 1
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',   // Name = "orders"
                    0, 0, 0, 3,               // NumPartitions = 3
                    0, 2,                     // ReplicationFactor = 2
                    0, 0, 0, 1,               // Assignments count = 1
                    0, 0, 0, 0,               // PartitionIndex = 0
                    0, 0, 0, 2,               // BrokerIds count = 2
                    0, 0, 0, 1, 0, 0, 0, 2,   // BrokerIds = [1, 2]
                    0, 0, 0, 2,               // Configs count = 2
                    0, 12, 'r', 'e', 't', 'e', 'n', 't', 'i', 'o', 'n', '.', 'm', 's', // "retention.ms"
                    0, 9, '6', '0', '4', '8', '0', '0', '0', '0', '0', // value = "604800000"
                    0, 14, 'c', 'l', 'e', 'a', 'n', 'u', 'p', '.', 'p', 'o', 'l', 'i', 'c', 'y', // "cleanup.policy"
                    0, 6, 'd', 'e', 'l', 'e', 't', 'e', // value = "delete"
                    0, 0, 117, 48,           // timeoutMs = 30000 (0x7530)
            });
        }

        @Test
        @DisplayName("v1 adds validateOnly byte (spec default false) after timeoutMs")
        void v1() {
            byte[] b = CreateTopicsCodec.encodeRequest((short) 1, req(30000));
            assertEquals(96, b.length); // v0 (95) + 1 validateOnly
            assertEquals(0, b[b.length - 1]); // validateOnly = false
        }

        @Test
        @DisplayName("v4 = v0 layout + validateOnly byte")
        void v4() {
            byte[] b = CreateTopicsCodec.encodeRequest((short) 4, req(30000));
            assertEquals(CreateTopicsCodec.encodeRequest((short) 1, req(30000)).length, b.length);
        }
    }

    // ── Flexible request (KIP-482, v5–v7) ──────────────────────────────

    @Nested
    @DisplayName("Flexible request (KIP-482)")
    class FlexibleRequest {

        @Test
        @DisplayName("v5 counts are varint(size + 1): numTopics=2, assignments=2, brokerIds=3, configs=3 (verified against Apache 3.6.1 generated code)")
        void countsArePlusOne() {
            byte[] b = CreateTopicsCodec.encodeRequest((short) 5, req(30000));
            // varint(1 + 1) = 2 — numTopics (1 topic)
            assertEquals(2, b[0]);
        }

        @Test
        @DisplayName("v5 full layout byte-for-byte (compact strings, N+1 array counts, per-struct endTags=0)")
        void v5Layout() {
            byte[] b = CreateTopicsCodec.encodeRequest((short) 5, req(30000));
            assertArrayEquals(b, new byte[]{
                    2,                                        // numTopics varint = 1 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',         // Name compact = varint(6+1)=7 + "orders"
                    0, 0, 0, 3,                               // NumPartitions = 3
                    0, 2,                                     // ReplicationFactor = 2
                    2,                                        // Assignments count = 1 + 1
                    0, 0, 0, 0,                               // PartitionIndex = 0
                    3,                                        // BrokerIds count = 2 + 1
                    0, 0, 0, 1, 0, 0, 0, 2,                   // BrokerIds = [1, 2]
                    0,                                        // per-assignment endTags = 0
                    3,                                        // Configs count = 2 + 1
                    13, 'r', 'e', 't', 'e', 'n', 't', 'i', 'o', 'n', '.', 'm', 's', // "retention.ms" (12+1)
                    10, '6', '0', '4', '8', '0', '0', '0', '0', '0', // value "604800000" (9+1)
                    0,                                        // per-config endTags = 0
                    15, 'c', 'l', 'e', 'a', 'n', 'u', 'p', '.', 'p', 'o', 'l', 'i', 'c', 'y', // "cleanup.policy" varint(14+1)=15
                    7, 'd', 'e', 'l', 'e', 't', 'e', // value "delete" varint(6+1)=7
                    0,                                        // per-config endTags = 0
                    0,                                        // per-topic endTags = 0
                    0, 0, 117, 48,                           // timeoutMs = 30000 (0x7530)
                    0,                                        // validateOnly = false
                    0,                                        // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v5 round-trip preserves name/partitions/replication/assignments/configs/timeout")
        void roundTripV5() {
            CreateTopicsRequest decoded = roundTripReq((short) 5, req(30000));
            CreateTopicsRequest.TopicCreate t = decoded.topics().get(0);
            assertEquals("orders", t.name());
            assertEquals(3, t.numPartitions());
            assertEquals((short) 2, t.replicationFactor());
            assertEquals(1, t.assignments().size());
            assertEquals(0, t.assignments().get(0).partitionIndex());
            assertEquals(List.of(1, 2), t.assignments().get(0).brokerIds());
            assertEquals(configs("retention.ms", "604800000", "cleanup.policy", "delete"), t.configs());
            assertEquals(30000, decoded.timeoutMs());
        }

        @Test
        @DisplayName("v6 round-trip (same flexible layout, no new request fields since v5)")
        void roundTripV6() {
            CreateTopicsRequest decoded = roundTripReq((short) 6, req(30000));
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(configs("retention.ms", "604800000", "cleanup.policy", "delete"),
                    decoded.topics().get(0).configs());
            assertEquals(30000, decoded.timeoutMs());
        }

        @Test
        @DisplayName("v7 round-trip (request layout unchanged from v5)")
        void roundTripV7() {
            CreateTopicsRequest decoded = roundTripReq((short) 7, req(30000));
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(3, decoded.topics().get(0).numPartitions());
            assertEquals(30000, decoded.timeoutMs());
        }

        @Test
        @DisplayName("empty assignments round-trip (count varint = 1)")
        void emptyAssignments() {
            CreateTopicsRequest r = new CreateTopicsRequest(List.of(new CreateTopicsRequest.TopicCreate(
                    "orders", 3, (short) 2, List.of(), configs("k", "v"))), 30000);
            byte[] b = CreateTopicsCodec.encodeRequest((short) 5, r);
            // After numTopics(1) + name(7) + partitions(4) + rf(2) the Assignments count varint = 0 + 1
            assertEquals(1, b[1 + 7 + 4 + 2]);
            CreateTopicsRequest decoded = roundTripReq((short) 5, r);
            assertEquals(0, decoded.topics().get(0).assignments().size());
        }

        @Test
        @DisplayName("empty configs round-trip (count varint = 1)")
        void emptyConfigs() {
            CreateTopicsRequest r = new CreateTopicsRequest(List.of(new CreateTopicsRequest.TopicCreate(
                    "orders", 3, (short) 2, List.of(), Map.of())), 30000);
            CreateTopicsRequest decoded = roundTripReq((short) 5, r);
            assertEquals(0, decoded.topics().get(0).configs().size());
        }
    }

    // ── Flexible response (KIP-482, v5–v7) ─────────────────────────────

    @Nested
    @DisplayName("Flexible response (KIP-482)")
    class FlexibleResponse {

        @Test
        @DisplayName("v5 layout: throttleTime + numTopics varint(2) + compact name + errorCode + ErrorMessage varint(0) + int32 partitions(-1) + int16 rf(-1) + Configs varint(0=null) + endTags")
        void v5Layout() {
            byte[] b = CreateTopicsCodec.encodeResponse((short) 5, resp("orders", (short) 0));
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                // ThrottleTimeMs = 0
                    2,                         // numTopics varint = 1 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's', // Name compact = 7 + "orders"
                    0, 0,                      // ErrorCode = 0
                    0,                         // ErrorMessage null (compact-nullable: varint 0)
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,  // NumPartitions = -1 (absent default)
                    (byte) 0xFF, (byte) 0xFF,  // ReplicationFactor = -1 (absent default)
                    0,                         // Configs null compact array (KIP-482: 0 = null)
                    0,                         // per-topic endTags = 0 (TopicConfigErrorCode = 0)
                    0,                         // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v7 layout adds 16-byte TopicId (all-zero uuid absent default) between Name and ErrorCode")
        void v7Layout() {
            byte[] b = CreateTopicsCodec.encodeResponse((short) 7, resp("orders", (short) 0));
            // v5 layout (24) + 16 TopicId
            assertEquals(24 + 16, b.length);
            // TopicId sits right after the compact name (offset 4 + 1 + 7 = 12)
            for (int i = 12; i < 12 + 16; i++) {
                assertEquals(0, b[i], "TopicId byte " + (i - 12));
            }
            // ErrorCode (short 0) immediately after TopicId
            assertEquals(0, b[12 + 16]);
            assertEquals(0, b[12 + 16 + 1]);
        }

        @Test
        @DisplayName("v5 round-trip preserves name/errorCode")
        void roundTripV5() {
            CreateTopicsResponse decoded = roundTripResp((short) 5, resp("orders", (short) 3));
            assertEquals(1, decoded.topics().size());
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(3, decoded.topics().get(0).errorCode());
        }

        @Test
        @DisplayName("v6 round-trip preserves name/errorCode")
        void roundTripV6() {
            CreateTopicsResponse decoded = roundTripResp((short) 6, resp("orders", (short) 0));
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(0, decoded.topics().get(0).errorCode());
        }

        @Test
        @DisplayName("v7 round-trip preserves name/errorCode (TopicId read + discarded)")
        void roundTripV7() {
            CreateTopicsResponse decoded = roundTripResp((short) 7, resp("orders", (short) 1));
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(1, decoded.topics().get(0).errorCode());
        }
    }

    // ── Fixed response layout ───────────────────────────────────────────

    @Nested
    @DisplayName("Fixed response layout")
    class FixedResponse {

        @Test
        @DisplayName("v0 = int32 numTopics + [name, int16 errorCode]")
        void v0() {
            byte[] b = CreateTopicsCodec.encodeResponse((short) 0, resp("orders", (short) 0));
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 1,                  // numTopics = 1
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',  // Name = "orders"
                    0, 0,                        // ErrorCode = 0
            });
        }

        @Test
        @DisplayName("v1 adds nullable ErrorMessage (absent = int16 -1); v2 adds int32 ThrottleTimeMs")
        void v1AndV2() {
            byte[] b1 = CreateTopicsCodec.encodeResponse((short) 1, resp("orders", (short) 0));
            assertEquals(4 + 8 + 2 + 2, b1.length); // numTopics + name + errorCode + len(-1)
            assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF},
                    new byte[]{b1[b1.length - 2], b1[b1.length - 1]});
            byte[] b2 = CreateTopicsCodec.encodeResponse((short) 2, resp("orders", (short) 0));
            assertEquals(b1.length + 4, b2.length); // + ThrottleTimeMs
            assertArrayEquals(new byte[]{0, 0, 0, 0},
                    new byte[]{b2[0], b2[1], b2[2], b2[3]}); // ThrottleTimeMs = 0
        }

        @Test
        @DisplayName("v4 round-trip preserves name/errorCode")
        void roundTripV4() {
            CreateTopicsResponse decoded = roundTripResp((short) 4, resp("orders", (short) 5));
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(5, decoded.topics().get(0).errorCode());
        }
    }

    // ── Model compat + version validation ───────────────────────────────

    @Nested
    @DisplayName("Model compat and version validation")
    class Compat {

        @Test
        @DisplayName("4-arg TopicCreate constructor defaults assignments to an empty list")
        void compatConstructor() {
            var t = new CreateTopicsRequest.TopicCreate("orders", 3, (short) 1, configs("k", "v"));
            assertEquals(List.of(), t.assignments());
            assertEquals(configs("k", "v"), t.configs());
        }

        @Test
        @DisplayName("out-of-range versions throw CodecNotImplementedException")
        void versionValidation() {
            assertThrows(CodecNotImplementedException.class,
                    () -> CreateTopicsCodec.encodeRequest((short) 8, req(30000)));
            assertThrows(CodecNotImplementedException.class,
                    () -> CreateTopicsCodec.decodeRequest((short) -1, ByteBuffer.wrap(new byte[0])));
            assertThrows(CodecNotImplementedException.class,
                    () -> CreateTopicsCodec.encodeResponse((short) 8, resp("t", (short) 0)));
            assertThrows(CodecNotImplementedException.class,
                    () -> CreateTopicsCodec.decodeResponse((short) 9, ByteBuffer.wrap(new byte[0])));
        }
    }
}
