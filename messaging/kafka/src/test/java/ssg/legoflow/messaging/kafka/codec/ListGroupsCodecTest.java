package ssg.legoflow.messaging.kafka.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.ListGroupsRequest;
import ssg.legoflow.messaging.kafka.protocol.ListGroupsResponse;
import ssg.legoflow.messaging.kafka.protocol.ListGroupsResponse.GroupListing;

/**
 * Golden-vector and round-trip tests for {@link ListGroupsCodec} across all
 * versions v0–v4.
 *
 * <p><b>Spec:</b> {@code doc/spec/message/ListGroupsRequest.json} /
 * {@code ListGroupsResponse.json} (API key 16).
 * <ul>
 *   <li>Request: v0–v2 empty body; v3 first flexible version (empty body +
 *       endTag); v4 adds StatesFilter (KIP-518). The in-house request model is
 *       empty, so StatesFilter is written as an empty array and read + discarded.</li>
 *   <li>Response: v0 ErrorCode + Groups; v1 adds ThrottleTimeMs; v3 first
 *       flexible version; v4 adds per-group GroupState (KIP-518).</li>
 * </ul>
 */
@DisplayName("ListGroupsCodec v0–v4")
class ListGroupsCodecTest {

    private static final ListGroupsRequest REQUEST = new ListGroupsRequest();

    private static final ListGroupsResponse RESPONSE = new ListGroupsResponse((short) 0, List.of(
            new GroupListing("g1", "consumer", "Stable"),
            new GroupListing("g2", "consumer", "Empty")));

    // ---------------- golden vectors ----------------
    // Generated from the codec and independently verified byte-for-byte against a
    // Python reference encoder built from the spec JSON (doc/spec/message/...).

    /** Request v0–v2: empty body. */
    private static final byte[] REQ_V0_GOLDEN = new byte[0];

    /** Request v3: flexible — top-level endTag only (empty body). */
    private static final byte[] REQ_V3_GOLDEN = fromHex(
            "00");                    // top-level endTag

    /** Request v4: flexible — StatesFilter count (0+1) then top-level endTag. */
    private static final byte[] REQ_V4_GOLDEN = fromHex(
            "01" +                    // StatesFilter count = 0 + 1 (KIP-482); empty array
                    "00");            // top-level endTag

    /** Response v0: ErrorCode + Groups count, no ThrottleTimeMs, no GroupState. */
    private static final byte[] RSP_V0_GOLDEN = fromHex(
            "0000" +                  // ErrorCode = 0
                    "00000002" +      // Groups count = 2 (int32)
                    "00026731" +      // g1 GroupId "g1"
                    "0008636F6E73756D6572" +  // g1 ProtocolType "consumer"
                    "00026732" +      // g2 GroupId "g2"
                    "0008636F6E73756D6572");  // g2 ProtocolType "consumer"

    /** Response v1: ThrottleTimeMs prepended. */
    private static final byte[] RSP_V1_GOLDEN = fromHex(
            "00000000" +              // ThrottleTimeMs = 0
                    "0000" +          // ErrorCode
                    "00000002" +
                    "00026731" +
                    "0008636F6E73756D6572" +
                    "00026732" +
                    "0008636F6E73756D6572");

    /** Response v2: wire-identical to v1 (quota-aware only). */
    private static final byte[] RSP_V2_GOLDEN = RSP_V1_GOLDEN;

    /** Response v3: flexible — varint count, compact strings, per-group + top-level endTags. */
    private static final byte[] RSP_V3_GOLDEN = fromHex(
            "00000000" +              // ThrottleTimeMs
                    "0000" +          // ErrorCode
                    "03" +            // Groups count = 2 + 1
                    "036731" +        // g1 GroupId compact "g1"
                    "09636F6E73756D6572" +  // g1 ProtocolType compact "consumer"
                    "00" +            // g1 endTag
                    "036732" +        // g2 GroupId compact "g2"
                    "09636F6E73756D6572" +  // g2 ProtocolType compact "consumer"
                    "00" +            // g2 endTag
                    "00");            // top-level endTag

    /** Response v4: flexible + per-group GroupState. */
    private static final byte[] RSP_V4_GOLDEN = fromHex(
            "00000000" +              // ThrottleTimeMs
                    "0000" +          // ErrorCode
                    "03" +            // Groups count = 2 + 1
                    "036731" +        // g1 GroupId
                    "09636F6E73756D6572" +  // g1 ProtocolType
                    "07537461626C65" +     // g1 GroupState compact "Stable"
                    "00" +            // g1 endTag
                    "036732" +        // g2 GroupId
                    "09636F6E73756D6572" +  // g2 ProtocolType
                    "06456D707479" +       // g2 GroupState compact "Empty"
                    "00" +            // g2 endTag
                    "00");            // top-level endTag

    // ---------------- request ----------------

    @Nested
    @DisplayName("Request")
    class Request {

        @Test
        @DisplayName("round-trip all versions")
        void roundTripAllVersions() {
            for (short v = 0; v <= 4; v++) {
                byte[] encoded = ListGroupsCodec.encodeRequest(v, REQUEST);
                ListGroupsRequest decoded =
                        ListGroupsCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
                assertThat(decoded).as("request v" + v).isEqualTo(REQUEST);
            }
        }

        @Test
        @DisplayName("v0–v2 golden (empty body)")
        void v0Golden() {
            for (short v = 0; v <= 2; v++) {
                assertThat(ListGroupsCodec.encodeRequest(v, REQUEST))
                        .as("request v" + v).isEqualTo(REQ_V0_GOLDEN);
            }
        }

        @Test
        @DisplayName("v3 flexible golden (endTag only)")
        void v3Golden() {
            assertThat(ListGroupsCodec.encodeRequest((short) 3, REQUEST))
                    .isEqualTo(REQ_V3_GOLDEN);
        }

        @Test
        @DisplayName("v4 golden (StatesFilter empty array)")
        void v4Golden() {
            assertThat(ListGroupsCodec.encodeRequest((short) 4, REQUEST))
                    .isEqualTo(REQ_V4_GOLDEN);
        }

        @Test
        @DisplayName("v4 StatesFilter is read + discarded on decode")
        void v4DiscardStatesFilter() {
            byte[] encoded = ListGroupsCodec.encodeRequest((short) 4, REQUEST);
            ListGroupsRequest decoded =
                    ListGroupsCodec.decodeRequest((short) 4, ByteBuffer.wrap(encoded));
            assertThat(decoded).isEqualTo(REQUEST);
        }
    }

    // ---------------- response ----------------

    @Nested
    @DisplayName("Response")
    class Response {

        @Test
        @DisplayName("round-trip all versions (field-level)")
        void roundTripAllVersions() {
            for (short v = 0; v <= 4; v++) {
                byte[] encoded = ListGroupsCodec.encodeResponse(v, RESPONSE);
                ListGroupsResponse decoded =
                        ListGroupsCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertResponseFields(v, decoded);
            }
        }

        @Test
        @DisplayName("v0 golden")
        void v0Golden() {
            assertThat(ListGroupsCodec.encodeResponse((short) 0, RESPONSE))
                    .isEqualTo(RSP_V0_GOLDEN);
        }

        @Test
        @DisplayName("v1 golden (ThrottleTimeMs)")
        void v1Golden() {
            assertThat(ListGroupsCodec.encodeResponse((short) 1, RESPONSE))
                    .isEqualTo(RSP_V1_GOLDEN);
        }

        @Test
        @DisplayName("v2 golden (wire-identical to v1)")
        void v2Golden() {
            assertThat(ListGroupsCodec.encodeResponse((short) 2, RESPONSE))
                    .isEqualTo(RSP_V2_GOLDEN);
        }

        @Test
        @DisplayName("v3 flexible golden")
        void v3Golden() {
            assertThat(ListGroupsCodec.encodeResponse((short) 3, RESPONSE))
                    .isEqualTo(RSP_V3_GOLDEN);
        }

        @Test
        @DisplayName("v4 golden (GroupState)")
        void v4Golden() {
            assertThat(ListGroupsCodec.encodeResponse((short) 4, RESPONSE))
                    .isEqualTo(RSP_V4_GOLDEN);
        }

        @Test
        @DisplayName("GroupState round-trips only at v4+; null at v0–v3")
        void groupStateVersionGate() {
            for (short v = 0; v <= 2; v++) {
                byte[] encoded = ListGroupsCodec.encodeResponse(v, RESPONSE);
                ListGroupsResponse decoded =
                        ListGroupsCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.groups().getFirst().state())
                        .as("v" + v + " GroupState").isNull();
            }
            for (short v = 3; v <= 3; v++) {
                byte[] encoded = ListGroupsCodec.encodeResponse(v, RESPONSE);
                ListGroupsResponse decoded =
                        ListGroupsCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.groups().getFirst().state())
                        .as("v" + v + " GroupState (flexible, pre-KIP-518)").isNull();
            }
            byte[] encoded = ListGroupsCodec.encodeResponse((short) 4, RESPONSE);
            ListGroupsResponse decoded =
                    ListGroupsCodec.decodeResponse((short) 4, ByteBuffer.wrap(encoded));
            assertThat(decoded).isEqualTo(RESPONSE);
        }

        @Test
        @DisplayName("v0 decodes ThrottleTimeMs-less body; v1+ skips it")
        void throttleTimeGate() {
            ListGroupsResponse v0 = new ListGroupsResponse((short) 7, List.of(
                    new GroupListing("g", "consumer", null)));
            byte[] e0 = ListGroupsCodec.encodeResponse((short) 0, v0);
            ListGroupsResponse d0 =
                    ListGroupsCodec.decodeResponse((short) 0, ByteBuffer.wrap(e0));
            assertThat(d0.errorCode()).isEqualTo((short) 7);
            assertThat(d0.groups()).hasSize(1);
            assertThat(d0.groups().getFirst().groupId()).isEqualTo("g");
        }
    }

    // ---------------- helpers ----------------

    private static void assertResponseFields(short v, ListGroupsResponse r) {
        assertThat(r.errorCode()).as("v" + v + " errorCode").isZero();
        assertThat(r.groups()).as("v" + v + " group count").hasSize(2);
        GroupListing g1 = r.groups().get(0);
        assertThat(g1.groupId()).isEqualTo("g1");
        assertThat(g1.protocolType()).isEqualTo("consumer");
        assertThat(g1.state()).as("v" + v + " GroupState")
                .isEqualTo(v >= 4 ? "Stable" : null);
        GroupListing g2 = r.groups().get(1);
        assertThat(g2.groupId()).isEqualTo("g2");
        assertThat(g2.protocolType()).isEqualTo("consumer");
        assertThat(g2.state()).as("v" + v + " GroupState")
                .isEqualTo(v >= 4 ? "Empty" : null);
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}
