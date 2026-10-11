/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ssg.legoflow.messaging.kafka.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.FindCoordinatorRequest;
import ssg.legoflow.messaging.kafka.protocol.FindCoordinatorResponse;

import java.nio.ByteBuffer;

/**
 * Unit tests for {@link FindCoordinatorCodec}.
 *
 * <p>Round-trip tests exercise every version v0–v4 for both request and response, with
 * byte-exact checks (frozen vectors) against the spec order tables in
 * {@code doc/spec/order/FindCoordinator.Request.txt} / {@code FindCoordinator.Response.txt}
 * and the Apache Kafka spec JSONs in {@code doc/spec/message/}.
 */
final class FindCoordinatorCodecTest {

    // ═══════════════════════════════════════════════════════════════════
    // Request — encode/decode round-trip
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Request — encode/decode round-trip")
    class RequestRoundTrip {

        @Test
        @DisplayName("v0 — key-only round-trip")
        void v0RoundTrip() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("group-1", FindCoordinatorRequest.KEY_TYPE_GROUP);
            byte[] bytes = FindCoordinatorCodec.encodeRequest((short) 0, req);
            FindCoordinatorRequest decoded = FindCoordinatorCodec.decodeRequest((short) 0, ByteBuffer.wrap(bytes));
            assertEquals("group-1", decoded.key());
            assertEquals(FindCoordinatorRequest.KEY_TYPE_GROUP, decoded.keyType());
        }

        @Test
        @DisplayName("v1 — key + keyType round-trip")
        void v1RoundTrip() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("group-1", FindCoordinatorRequest.KEY_TYPE_GROUP);
            byte[] bytes = FindCoordinatorCodec.encodeRequest((short) 1, req);
            FindCoordinatorRequest decoded = FindCoordinatorCodec.decodeRequest((short) 1, ByteBuffer.wrap(bytes));
            assertEquals("group-1", decoded.key());
            assertEquals(FindCoordinatorRequest.KEY_TYPE_GROUP, decoded.keyType());
        }

        @Test
        @DisplayName("v2 — key + transaction keyType round-trip")
        void v2TransactionRoundTrip() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("txn-1", FindCoordinatorRequest.KEY_TYPE_TRANSACTION);
            byte[] bytes = FindCoordinatorCodec.encodeRequest((short) 2, req);
            FindCoordinatorRequest decoded = FindCoordinatorCodec.decodeRequest((short) 2, ByteBuffer.wrap(bytes));
            assertEquals("txn-1", decoded.key());
            assertEquals(FindCoordinatorRequest.KEY_TYPE_TRANSACTION, decoded.keyType());
        }

        @Test
        @DisplayName("v3 — flexible encoding round-trip")
        void v3FlexibleRoundTrip() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("flex-group", FindCoordinatorRequest.KEY_TYPE_GROUP);
            byte[] bytes = FindCoordinatorCodec.encodeRequest((short) 3, req);
            FindCoordinatorRequest decoded = FindCoordinatorCodec.decodeRequest((short) 3, ByteBuffer.wrap(bytes));
            assertEquals("flex-group", decoded.key());
            assertEquals(FindCoordinatorRequest.KEY_TYPE_GROUP, decoded.keyType());
        }

        @Test
        @DisplayName("v4 — batched (KIP-699) single key round-trip")
        void v4BatchRoundTrip() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("group-1", FindCoordinatorRequest.KEY_TYPE_GROUP);
            byte[] bytes = FindCoordinatorCodec.encodeRequest((short) 4, req);
            FindCoordinatorRequest decoded = FindCoordinatorCodec.decodeRequest((short) 4, ByteBuffer.wrap(bytes));
            assertEquals("group-1", decoded.key());
            assertEquals(FindCoordinatorRequest.KEY_TYPE_GROUP, decoded.keyType());
        }

        @Test
        @DisplayName("empty key round-trips at v0 and v4")
        void emptyKey() {
            FindCoordinatorRequest v0 = new FindCoordinatorRequest("", FindCoordinatorRequest.KEY_TYPE_GROUP);
            FindCoordinatorRequest d0 =
                    FindCoordinatorCodec.decodeRequest((short) 0, ByteBuffer.wrap(FindCoordinatorCodec.encodeRequest((short) 0, v0)));
            assertEquals("", d0.key());

            FindCoordinatorRequest v4 = new FindCoordinatorRequest("", FindCoordinatorRequest.KEY_TYPE_GROUP);
            FindCoordinatorRequest d4 =
                    FindCoordinatorCodec.decodeRequest((short) 4, ByteBuffer.wrap(FindCoordinatorCodec.encodeRequest((short) 4, v4)));
            assertEquals("", d4.key());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Request — byte-exact encode (frozen vectors)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Request — byte-exact encode")
    class RequestBytes {

        @Test
        @DisplayName("v0 — int16-length-prefixed key")
        void v0Key() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("ab", FindCoordinatorRequest.KEY_TYPE_GROUP);
            byte[] bytes = FindCoordinatorCodec.encodeRequest((short) 0, req);
            assertArrayEquals(new byte[]{0, 2, 'a', 'b'}, bytes);
        }

        @Test
        @DisplayName("v1 — key first, then keyType (int8)")
        void v1Order() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("ab", FindCoordinatorRequest.KEY_TYPE_TRANSACTION);
            byte[] bytes = FindCoordinatorCodec.encodeRequest((short) 1, req);
            // Key(int16 len 2, "ab"), KeyType(1)
            assertArrayEquals(new byte[]{0, 2, 'a', 'b', 1}, bytes);
        }

        @Test
        @DisplayName("v3 — flexible compact key + keyType + end-tags(0)")
        void v3Flexible() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("ab", FindCoordinatorRequest.KEY_TYPE_GROUP);
            byte[] bytes = FindCoordinatorCodec.encodeRequest((short) 3, req);
            // Key: compact varint(3) "ab", KeyType(0), top-level end-tags varint(0)
            assertArrayEquals(new byte[]{3, 'a', 'b', 0, 0}, bytes);
        }

        @Test
        @DisplayName("v4 — keyType, then CoordinatorKeys[count=2 (N+1), compact key] + end-tags(0)")
        void v4Batch() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("ab", FindCoordinatorRequest.KEY_TYPE_TRANSACTION);
            byte[] bytes = FindCoordinatorCodec.encodeRequest((short) 4, req);
            // KeyType(1), count varint(2), compact varint(3) "ab", end-tags varint(0)
            assertArrayEquals(new byte[]{1, 2, 3, 'a', 'b', 0}, bytes);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Response — encode/decode round-trip
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Response — encode/decode round-trip")
    class ResponseRoundTrip {

        @Test
        @DisplayName("v0 — errorCode, nodeId, host, port")
        void v0RoundTrip() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 42, "coordinator.example.com", 9092);
            byte[] bytes = FindCoordinatorCodec.encodeResponse((short) 0, resp);
            FindCoordinatorResponse decoded = FindCoordinatorCodec.decodeResponse((short) 0, ByteBuffer.wrap(bytes));
            assertEquals((short) 0, decoded.errorCode());
            assertEquals(42, decoded.nodeId());
            assertEquals("coordinator.example.com", decoded.host());
            assertEquals(9092, decoded.port());
        }

        @Test
        @DisplayName("v1 — throttleTime + nullable errorMessage (null) round-trip")
        void v1RoundTrip() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 5, 42, "h", 9092);
            byte[] bytes = FindCoordinatorCodec.encodeResponse((short) 1, resp);
            FindCoordinatorResponse decoded = FindCoordinatorCodec.decodeResponse((short) 1, ByteBuffer.wrap(bytes));
            assertEquals((short) 5, decoded.errorCode());
            assertEquals(42, decoded.nodeId());
            assertEquals("h", decoded.host());
            assertEquals(9092, decoded.port());
        }

        @Test
        @DisplayName("v3 — flexible encoding round-trip")
        void v3FlexibleRoundTrip() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 42, "flex.host", 9092);
            byte[] bytes = FindCoordinatorCodec.encodeResponse((short) 3, resp);
            FindCoordinatorResponse decoded = FindCoordinatorCodec.decodeResponse((short) 3, ByteBuffer.wrap(bytes));
            assertEquals((short) 0, decoded.errorCode());
            assertEquals(42, decoded.nodeId());
            assertEquals("flex.host", decoded.host());
            assertEquals(9092, decoded.port());
        }

        @Test
        @DisplayName("v4 — batched Coordinators (KIP-699) round-trip")
        void v4BatchRoundTrip() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 42, "batch.host", 9092);
            byte[] bytes = FindCoordinatorCodec.encodeResponse((short) 4, resp);
            FindCoordinatorResponse decoded = FindCoordinatorCodec.decodeResponse((short) 4, ByteBuffer.wrap(bytes));
            assertEquals((short) 0, decoded.errorCode());
            assertEquals(42, decoded.nodeId());
            assertEquals("batch.host", decoded.host());
            assertEquals(9092, decoded.port());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Response — byte-exact encode (frozen vectors)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Response — byte-exact encode")
    class ResponseBytes {

        @Test
        @DisplayName("v0 — 13 bytes: errorCode(2) nodeId(4) host(2+1) port(4)")
        void v0Exact() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 1, "h", 1);
            byte[] bytes = FindCoordinatorCodec.encodeResponse((short) 0, resp);
            // ErrorCode(0,0) NodeId(0,0,0,1) Host(0,1,'h') Port(0,0,0,1)
            assertArrayEquals(new byte[]{
                    0, 0,
                    0, 0, 0, 1,
                    0, 1, 'h',
                    0, 0, 0, 1
            }, bytes);
            assertEquals(13, bytes.length);
        }

        @Test
        @DisplayName("v1 — ThrottleTimeMs(4) + nullable ErrorMessage(-1) + errorCode/nodeId/host/port")
        void v1Exact() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 1, "h", 1);
            byte[] bytes = FindCoordinatorCodec.encodeResponse((short) 1, resp);
            // Throttle(0,0,0,0) ErrCode(0,0) ErrMsg(-1) NodeId(0,0,0,1) Host(0,1,'h') Port(0,0,0,1)
            assertArrayEquals(new byte[]{
                    0, 0, 0, 0,
                    0, 0,
                    (byte) 0xFF, (byte) 0xFF,
                    0, 0, 0, 1,
                    0, 1, 'h',
                    0, 0, 0, 1
            }, bytes);
            assertEquals(19, bytes.length);
        }

        @Test
        @DisplayName("v3 — flexible: Throttle(4) ErrCode(2) null compact(1) NodeId(4) compact host(1+1) Port(4) end-tags(1)")
        void v3FlexibleExact() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 1, "h", 1);
            byte[] bytes = FindCoordinatorCodec.encodeResponse((short) 3, resp);
            // Throttle(0,0,0,0) ErrCode(0,0) ErrMsg compact null(0) NodeId(0,0,0,1) Host compact(2,'h') Port(0,0,0,1) end-tags(0)
            assertArrayEquals(new byte[]{
                    0, 0, 0, 0,
                    0, 0,
                    0,
                    0, 0, 0, 1,
                    2, 'h',
                    0, 0, 0, 1,
                    0
            }, bytes);
            assertEquals(18, bytes.length);
        }

        @Test
        @DisplayName("v4 — flexible batch: count(1) + Coordinator struct + struct trailer + top-level end-tags")
        void v4BatchExact() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 1, "h", 1);
            byte[] bytes = FindCoordinatorCodec.encodeResponse((short) 4, resp);
            // Throttle(0,0,0,0) count varint(2)
            // Coordinator: Key compact empty(1) NodeId(0,0,0,1) Host compact(2,'h') Port(0,0,0,1)
            //              ErrCode(0,0) ErrMsg compact null(0) struct end-tags(0)
            // top-level end-tags(0)
            assertArrayEquals(new byte[]{
                    0, 0, 0, 0,
                    2,
                    1,
                    0, 0, 0, 1,
                    2, 'h',
                    0, 0, 0, 1,
                    0, 0,
                    0,
                    0,
                    0
            }, bytes);
            assertEquals(21, bytes.length);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Version range
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Version range")
    class VersionRange {

        @Test
        @DisplayName("encodeRequest/decodeRequest throw CodecNotImplementedException outside 0-4")
        void requestOutOfRange() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("k", FindCoordinatorRequest.KEY_TYPE_GROUP);
            assertThrows(CodecNotImplementedException.class,
                    () -> FindCoordinatorCodec.encodeRequest((short) -1, req));
            assertThrows(CodecNotImplementedException.class,
                    () -> FindCoordinatorCodec.encodeRequest((short) 5, req));
            assertThrows(CodecNotImplementedException.class,
                    () -> FindCoordinatorCodec.decodeRequest((short) 5, ByteBuffer.allocate(0)));
        }

        @Test
        @DisplayName("encodeResponse/decodeResponse throw CodecNotImplementedException outside 0-4")
        void responseOutOfRange() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 1, "h", 1);
            assertThrows(CodecNotImplementedException.class,
                    () -> FindCoordinatorCodec.encodeResponse((short) 5, resp));
            assertThrows(CodecNotImplementedException.class,
                    () -> FindCoordinatorCodec.decodeResponse((short) -1, ByteBuffer.allocate(0)));
        }

        @Test
        @DisplayName("all versions 0-4 encode/decode without exception")
        void allVersions() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("k", FindCoordinatorRequest.KEY_TYPE_GROUP);
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 1, "h", 1);
            for (short v = 0; v <= 4; v++) {
                FindCoordinatorRequest rd = FindCoordinatorCodec.decodeRequest(v, ByteBuffer.wrap(FindCoordinatorCodec.encodeRequest(v, req)));
                assertEquals("k", rd.key());
                FindCoordinatorResponse rs = FindCoordinatorCodec.decodeResponse(v, ByteBuffer.wrap(FindCoordinatorCodec.encodeResponse(v, resp)));
                assertEquals("h", rs.host());
            }
            assertTrue(true);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Defaults
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        @DisplayName("version-less overloads use v0 (the pinned version)")
        void defaultsToPinnedVersion() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("ab", FindCoordinatorRequest.KEY_TYPE_GROUP);
            byte[] defaultRequest = FindCoordinatorCodec.encodeRequest(req);
            byte[] explicitV0Request = FindCoordinatorCodec.encodeRequest((short) 0, req);
            assertArrayEquals(explicitV0Request, defaultRequest);

            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 1, "h", 1);
            byte[] defaultResponse = FindCoordinatorCodec.encodeResponse(resp);
            byte[] explicitV0Response = FindCoordinatorCodec.encodeResponse((short) 0, resp);
            assertArrayEquals(explicitV0Response, defaultResponse);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Facade delegation
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Facade delegation")
    class FacadeDelegation {

        @Test
        @DisplayName("KafkaCodec.encodeFindCoordinatorRequest matches the dedicated codec")
        void facade_request_encode_matches_dedicatedCodec() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("facade-group", FindCoordinatorRequest.KEY_TYPE_GROUP);
            assertArrayEquals(
                    FindCoordinatorCodec.encodeRequest(req),
                    KafkaCodec.encodeFindCoordinatorRequest(req),
                    "facade encode must delegate to the dedicated codec");
        }

        @Test
        @DisplayName("KafkaCodec.encodeFindCoordinatorResponse matches the dedicated codec")
        void facade_response_encode_matches_dedicatedCodec() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 3, 7, "facade.host", 9093);
            assertArrayEquals(
                    FindCoordinatorCodec.encodeResponse(resp),
                    KafkaCodec.encodeFindCoordinatorResponse(resp),
                    "facade encode must delegate to the dedicated codec");
        }

        @Test
        @DisplayName("KafkaCodec.decodeFindCoordinatorRequest forwards to the dedicated codec at the pinned version")
        void facade_request_decode_matches_dedicatedCodec() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("facade-group", FindCoordinatorRequest.KEY_TYPE_TRANSACTION);
            byte[] enc = KafkaCodec.encodeFindCoordinatorRequest(req);
            FindCoordinatorRequest viaFacade = KafkaCodec.decodeFindCoordinatorRequest(ByteBuffer.wrap(enc));
            FindCoordinatorRequest viaCodec = FindCoordinatorCodec.decodeRequest(FindCoordinatorCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
            assertEquals(viaCodec, viaFacade,
                    "facade decode must forward to the dedicated codec at the pinned version");
        }

        @Test
        @DisplayName("KafkaCodec.decodeFindCoordinatorResponse forwards to the dedicated codec at the pinned version")
        void facade_response_decode_matches_dedicatedCodec() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 0, 9, "facade.host", 9093);
            byte[] enc = KafkaCodec.encodeFindCoordinatorResponse(resp);
            FindCoordinatorResponse viaFacade = KafkaCodec.decodeFindCoordinatorResponse(ByteBuffer.wrap(enc));
            FindCoordinatorResponse viaCodec = FindCoordinatorCodec.decodeResponse(FindCoordinatorCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
            assertEquals(viaCodec, viaFacade,
                    "facade decode must forward to the dedicated codec at the pinned version");
        }

        @Test
        @DisplayName("facade round-trip request")
        void facade_roundTrip_request() {
            FindCoordinatorRequest req = new FindCoordinatorRequest("facade-group", FindCoordinatorRequest.KEY_TYPE_GROUP);
            FindCoordinatorRequest dec = KafkaCodec.decodeFindCoordinatorRequest(
                    ByteBuffer.wrap(KafkaCodec.encodeFindCoordinatorRequest(req)));
            assertEquals(req, dec);
        }

        @Test
        @DisplayName("facade round-trip response")
        void facade_roundTrip_response() {
            FindCoordinatorResponse resp = new FindCoordinatorResponse((short) 1, 4, "facade.host", 9093);
            FindCoordinatorResponse dec = KafkaCodec.decodeFindCoordinatorResponse(
                    ByteBuffer.wrap(KafkaCodec.encodeFindCoordinatorResponse(resp)));
            assertEquals(resp, dec);
        }
    }
}
