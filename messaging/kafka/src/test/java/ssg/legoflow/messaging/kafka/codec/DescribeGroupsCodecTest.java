package ssg.legoflow.messaging.kafka.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.DescribeGroupsRequest;
import ssg.legoflow.messaging.kafka.protocol.DescribeGroupsResponse;
import ssg.legoflow.messaging.kafka.protocol.DescribeGroupsResponse.GroupDescription;
import ssg.legoflow.messaging.kafka.protocol.DescribeGroupsResponse.MemberDescription;

/**
 * Golden-vector and round-trip tests for {@link DescribeGroupsCodec} across all
 * versions v0–v5.
 *
 * <p><b>Spec:</b> {@code doc/spec/message/DescribeGroupsRequest.json} /
 * {@code DescribeGroupsResponse.json} (API key 15).
 * <ul>
 *   <li>v0: baseline.</li>
 *   <li>v1: response adds ThrottleTimeMs.</li>
 *   <li>v2: quota-aware response (wire format unchanged).</li>
 *   <li>v3: request adds IncludeAuthorizedOperations; response adds per-group
 *       AuthorizedOperations.</li>
 *   <li>v4: response members add nullable GroupInstanceId.</li>
 *   <li>v5: first flexible version (KIP-482: varint/compact string encoding,
 *       tagged fields, N+1 array counts).</li>
 * </ul>
 */
@DisplayName("DescribeGroupsCodec v0–v5")
class DescribeGroupsCodecTest {

    private static final byte[] MEMBER_METADATA = {1, 2, 3};
    private static final byte[] MEMBER_ASSIGNMENT = {(byte) 0xDE, (byte) 0xAD};

    private static final DescribeGroupsRequest REQUEST =
            new DescribeGroupsRequest(List.of("g1", "g2"));

    private static final DescribeGroupsResponse RESPONSE = new DescribeGroupsResponse(
            List.of(
                    new GroupDescription(
                            (short) 0, "g1", "Stable", "consumer", "{\"a\":1}",
                            List.of(new MemberDescription(
                                    "m1", "gi1", "ch1", MEMBER_METADATA, MEMBER_ASSIGNMENT))),
                    new GroupDescription(
                            (short) 0, "g2", "Empty", null, null,
                            List.of(new MemberDescription(
                                    "m2", "gi2", "ch2", MEMBER_METADATA, MEMBER_ASSIGNMENT)))));

    // ---------------- golden vectors ----------------
    // Generated from the codec and independently verified byte-for-byte against a
    // Python reference encoder built from the spec JSON (doc/spec/message/...).

    /** Request v0–v2: int32 count=2 + "g1" + "g2". */
    private static final byte[] REQ_V0_GOLDEN = fromHex(
            "00000002" +              // Groups count = 2 (int32)
                    "00026731" +      // "g1"
                    "00026732");      // "g2"

    /** Request v3–v4: v0 fields + IncludeAuthorizedOperations = false. */
    private static final byte[] REQ_V3_GOLDEN = fromHex(
            "00000002" +              // Groups count = 2
                    "00026731" +      // "g1"
                    "00026732" +      // "g2"
                    "00");            // IncludeAuthorizedOperations = false

    /** Request v5: flexible — compact arrays, varint(0) for false, endTag. */
    private static final byte[] REQ_V5_GOLDEN = fromHex(
            "03" +                    // Groups count = 2 + 1
                    "036731" +        // compact "g1"
                    "036732" +        // compact "g2"
                    "00" +            // IncludeAuthorizedOperations = false
                    "00");            // endTag

    /** Response v0: 2 groups, no throttle, no authorized ops. */
    private static final byte[] RSP_V0_GOLDEN = fromHex(
            "00000002" +              // Groups count = 2 (int32)
                    "0000" +          // ErrorCode = 0
                    "00026731" +      // GroupId "g1"
                    "0006537461626C65" +  // GroupState "Stable"
                    "0008636F6E73756D6572" + // ProtocolType "consumer"
                    "00077B2261223A317D" +   // ProtocolData "{\"a\":1}"
                    "00000001" +      // Members count = 1
                    "00026D31" +      // MemberId "m1"
                    "0003676931" +    // ClientId "gi1"
                    "0003636831" +    // ClientHost "ch1"
                    "00000003010203" + // MemberMetadata {1,2,3}
                    "00000002DEAD" +  // MemberAssignment {0xDE,0xAD}
                    "0000" +          // Groups[1] ErrorCode = 0
                    "00026732" +      // GroupId "g2" (int16 length + 2)
                    "0005456D707479" + // GroupState "Empty"
                    "FFFF" +          // ProtocolType null (int16 -1)
                    "FFFF" +          // ProtocolData null (int16 -1)
                    "00000001" +      // Members count = 1
                    "00026D32" +      // "m2"
                    "0003676932" +    // "gi2"
                    "0003636832" +    // "ch2"
                    "00000003010203" + // MemberMetadata
                    "00000002DEAD");   // MemberAssignment

    private static final byte[] RSP_V1_GOLDEN = fromHex(
            "00000000" +              // ThrottleTimeMs = 0
                    "00000002" +      // Groups count = 2
                    "0000" +          // ErrorCode
                    "00026731" +      // "g1"
                    "0006537461626C65" +
                    "0008636F6E73756D6572" +
                    "00077B2261223A317D" +
                    "00000001" +
                    "00026D31" +
                    "0003676931" +
                    "0003636831" +
                    "00000003010203" +
                    "00000002DEAD" +
                    "0000" +          // Groups[1] ErrorCode
                    "00026732" +      // "g2"
                    "0005456D707479" + // "Empty"
                    "FFFF" +
                    "FFFF" +
                    "00000001" +
                    "00026D32" +
                    "0003676932" +
                    "0003636832" +
                    "00000003010203" +
                    "00000002DEAD");

    private static final byte[] RSP_V2_GOLDEN = fromHex(
            "00000000" +              // ThrottleTimeMs
                    "00000002" +
                    "0000" +
                    "00026731" +
                    "0006537461626C65" +
                    "0008636F6E73756D6572" +
                    "00077B2261223A317D" +
                    "00000001" +
                    "00026D31" +
                    "0003676931" +
                    "0003636831" +
                    "00000003010203" +
                    "00000002DEAD" +
                    "0000" +
                    "00026732" +
                    "0005456D707479" +
                    "FFFF" +
                    "FFFF" +
                    "00000001" +
                    "00026D32" +
                    "0003676932" +
                    "0003636832" +
                    "00000003010203" +
                    "00000002DEAD");

    /** Response v3: v2 + per-group AuthorizedOperations = Integer.MIN_VALUE. */
    private static final byte[] RSP_V3_GOLDEN = fromHex(
            "00000000" +              // ThrottleTimeMs
                    "00000002" +
                    "0000" +
                    "00026731" +
                    "0006537461626C65" +
                    "0008636F6E73756D6572" +
                    "00077B2261223A317D" +
                    "00000001" +
                    "00026D31" +
                    "0003676931" +
                    "0003636831" +
                    "00000003010203" +
                    "00000002DEAD" +
                    "80000000" +      // AuthorizedOperations = Integer.MIN_VALUE
                    "0000" +
                    "00026732" +
                    "0005456D707479" +
                    "FFFF" +
                    "FFFF" +
                    "00000001" +
                    "00026D32" +
                    "0003676932" +
                    "0003636832" +
                    "00000003010203" +
                    "00000002DEAD" +
                    "80000000");      // AuthorizedOperations

    /** Response v4: v3 + nullable GroupInstanceId (int16 -1). */
    private static final byte[] RSP_V4_GOLDEN = fromHex(
            "00000000" +              // ThrottleTimeMs
                    "00000002" +
                    "0000" +
                    "00026731" +
                    "0006537461626C65" +
                    "0008636F6E73756D6572" +
                    "00077B2261223A317D" +
                    "00000001" +
                    "00026D31" +      // MemberId "m1"
                    "FFFF" +          // GroupInstanceId null (int16 -1)
                    "0003676931" +    // ClientId "gi1"
                    "0003636831" +    // ClientHost "ch1"
                    "00000003010203" +
                    "00000002DEAD" +
                    "80000000" +      // AuthorizedOperations
                    "0000" +
                    "00026732" +
                    "0005456D707479" +
                    "FFFF" +
                    "FFFF" +
                    "00000001" +
                    "00026D32" +
                    "FFFF" +          // GroupInstanceId null
                    "0003676932" +
                    "0003636832" +
                    "00000003010203" +
                    "00000002DEAD" +
                    "80000000");

    /** Response v5: flexible encoding, varint(0) nulls, endTags. */
    private static final byte[] RSP_V5_GOLDEN = fromHex(
            "00000000" +              // ThrottleTimeMs
                    "03" +            // Groups count = 2 + 1
                    "0000" +          // ErrorCode
                    "036731" +        // compact "g1"
                    "07537461626C65" + // compact "Stable"
                    "09636F6E73756D6572" + // compact "consumer"
                    "087B2261223A317D" +   // compact "{\"a\":1}"
                    "02" +            // Members count = 1 + 1
                    "036D31" +        // compact "m1"
                    "00" +            // GroupInstanceId null (varint(0))
                    "04676931" +      // compact "gi1"
                    "04636831" +      // compact "ch1"
                    "04010203" +      // compact bytes {1,2,3}
                    "03DEAD" +        // compact bytes {0xDE,0xAD}
                    "00" +            // per-member endTag
                    "80000000" +      // AuthorizedOperations (int32, fixed)
                    "00" +            // per-group endTag
                    "0000" +          // ErrorCode
                    "036732" +        // compact "g2"
                    "06456D707479" +  // compact "Empty"
                    "00" +            // ProtocolType null (varint(0))
                    "00" +            // ProtocolData null (varint(0))
                    "02" +            // Members count = 1 + 1
                    "036D32" +        // compact "m2"
                    "00" +            // GroupInstanceId null
                    "04676932" +
                    "04636832" +
                    "04010203" +
                    "03DEAD" +
                    "00" +            // per-member endTag
                    "80000000" +      // AuthorizedOperations
                    "00" +            // per-group endTag
                    "00");            // top-level endTag

    // ---------------- request ----------------

    @Nested
    @DisplayName("Request")
    class Request {

        @Test
        @DisplayName("round-trip all versions")
        void roundTripAllVersions() {
            for (short v = 0; v <= 5; v++) {
                byte[] encoded = DescribeGroupsCodec.encodeRequest(v, REQUEST);
                DescribeGroupsRequest decoded =
                        DescribeGroupsCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
                assertThat(decoded).as("request v" + v).isEqualTo(REQUEST);
            }
        }

        @Test
        @DisplayName("v0–v2 golden")
        void v0Golden() {
            for (short v = 0; v <= 2; v++) {
                assertThat(DescribeGroupsCodec.encodeRequest(v, REQUEST))
                        .as("request v" + v).isEqualTo(REQ_V0_GOLDEN);
            }
        }

        @Test
        @DisplayName("v3–v4 golden (IncludeAuthorizedOperations=false)")
        void v3Golden() {
            for (short v = 3; v <= 4; v++) {
                assertThat(DescribeGroupsCodec.encodeRequest(v, REQUEST))
                        .as("request v" + v).isEqualTo(REQ_V3_GOLDEN);
            }
        }

        @Test
        @DisplayName("v5 flexible golden")
        void v5Golden() {
            assertThat(DescribeGroupsCodec.encodeRequest((short) 5, REQUEST))
                    .isEqualTo(REQ_V5_GOLDEN);
        }

        @Test
        @DisplayName("v5 flexible: IncludeAuthorizedOperations encodes as varint(0) (spec default false)")
        void flexibleFlag() {
            var req = new DescribeGroupsRequest(List.of("g1"));
            byte[] encoded = DescribeGroupsCodec.encodeRequest((short) 5, req);
            // compact "g1" (03 6731) then varint(0) then endTag
            assertThat(encoded).hasSize(6);
            assertThat(encoded).isEqualTo(fromHex("02" + "036731" + "00" + "00"));
            assertThat(DescribeGroupsCodec.decodeRequest((short) 5,
                    ByteBuffer.wrap(encoded))).isEqualTo(req);
        }
    }

    // ---------------- response ----------------

    @Nested
    @DisplayName("Response")
    class Response {

        @Test
        @DisplayName("round-trip all versions (field-level)")
        void roundTripAllVersions() {
            for (short v = 0; v <= 5; v++) {
                byte[] encoded = DescribeGroupsCodec.encodeResponse(v, RESPONSE);
                DescribeGroupsResponse decoded =
                        DescribeGroupsCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertResponseFields(v, decoded);
            }
        }

        @Test
        @DisplayName("v0 golden")
        void v0Golden() {
            assertThat(DescribeGroupsCodec.encodeResponse((short) 0, RESPONSE))
                    .isEqualTo(RSP_V0_GOLDEN);
        }

        @Test
        @DisplayName("v1 golden (ThrottleTimeMs)")
        void v1Golden() {
            assertThat(DescribeGroupsCodec.encodeResponse((short) 1, RESPONSE))
                    .isEqualTo(RSP_V1_GOLDEN);
        }

        @Test
        @DisplayName("v2 golden")
        void v2Golden() {
            assertThat(DescribeGroupsCodec.encodeResponse((short) 2, RESPONSE))
                    .isEqualTo(RSP_V2_GOLDEN);
        }

        @Test
        @DisplayName("v3 golden (AuthorizedOperations)")
        void v3Golden() {
            assertThat(DescribeGroupsCodec.encodeResponse((short) 3, RESPONSE))
                    .isEqualTo(RSP_V3_GOLDEN);
        }

        @Test
        @DisplayName("v4 golden (GroupInstanceId)")
        void v4Golden() {
            assertThat(DescribeGroupsCodec.encodeResponse((short) 4, RESPONSE))
                    .isEqualTo(RSP_V4_GOLDEN);
        }

        @Test
        @DisplayName("v5 flexible golden")
        void v5Golden() {
            assertThat(DescribeGroupsCodec.encodeResponse((short) 5, RESPONSE))
                    .isEqualTo(RSP_V5_GOLDEN);
        }

        @Test
        @DisplayName("v5 flexible null protocol fields encode as varint(0)")
        void flexibleNullFields() {
            var resp = new DescribeGroupsResponse(List.of(
                    new GroupDescription(
                            (short) 0, "g", "Stable", null, null, List.of())));
            byte[] encoded = DescribeGroupsCodec.encodeResponse((short) 5, resp);
            // ThrottleTimeMs(4) + Groups count(1) + ErrorCode(2) + "g"(2) +
            // "Stable"(7) + ProtocolType null(1) + ProtocolData null(1) +
            // Members count(1) + AuthorizedOperations(4) + per-group endTag(1)
            // + top-level endTag(1) = 25
            assertThat(encoded).hasSize(25);
            assertThat(encoded[16]).as("ProtocolType null marker").isEqualTo((byte) 0);
            assertThat(encoded[17]).as("ProtocolData null marker").isEqualTo((byte) 0);
            DescribeGroupsResponse decoded =
                    DescribeGroupsCodec.decodeResponse((short) 5, ByteBuffer.wrap(encoded));
            assertThat(decoded.groups().getFirst().protocol()).isNull();
        }

        @Test
        @DisplayName("v4 GroupInstanceId round-trips as null")
        void groupInstanceIdRoundTrip() {
            var resp = new DescribeGroupsResponse(List.of(
                    new GroupDescription(
                            (short) 0, "g", "Stable", "consumer", "d",
                            List.of(new MemberDescription(
                                    "m1", "c1", "h1", MEMBER_METADATA, MEMBER_ASSIGNMENT)))));
            byte[] encoded = DescribeGroupsCodec.encodeResponse((short) 4, resp);
            DescribeGroupsResponse decoded =
                    DescribeGroupsCodec.decodeResponse((short) 4, ByteBuffer.wrap(encoded));
            assertThat(decoded.groups()).hasSize(1);
            assertThat(decoded.groups().getFirst().members()).hasSize(1);
            assertThat(decoded.groups().getFirst().protocolType()).isEqualTo("consumer");
            assertThat(decoded.groups().getFirst().protocol()).isEqualTo("d");
        }
    }

    // ---------------- helpers ----------------

    private static void assertResponseFields(short v, DescribeGroupsResponse r) {
        assertThat(r.groups()).as("v" + v + " group count").hasSize(2);
        GroupDescription g1 = r.groups().get(0);
        assertThat(g1.errorCode()).isZero();
        assertThat(g1.groupId()).isEqualTo("g1");
        assertThat(g1.state()).isEqualTo("Stable");
        assertThat(g1.protocolType()).isEqualTo("consumer");
        assertThat(g1.protocol()).isEqualTo("{\"a\":1}");
        assertThat(g1.members()).hasSize(1);
        MemberDescription m1 = g1.members().getFirst();
        assertThat(m1.memberId()).isEqualTo("m1");
        assertThat(m1.clientId()).isEqualTo("gi1");
        assertThat(m1.clientHost()).isEqualTo("ch1");
        assertThat(m1.memberMetadata()).isEqualTo(MEMBER_METADATA);
        assertThat(m1.memberAssignment()).isEqualTo(MEMBER_ASSIGNMENT);

        GroupDescription g2 = r.groups().get(1);
        assertThat(g2.groupId()).isEqualTo("g2");
        assertThat(g2.state()).isEqualTo("Empty");
        assertThat(g2.protocolType()).isNull();
        assertThat(g2.protocol()).isNull();
        MemberDescription m2 = g2.members().getFirst();
        assertThat(m2.memberId()).isEqualTo("m2");
        assertThat(m2.clientId()).isEqualTo("gi2");
        assertThat(m2.clientHost()).isEqualTo("ch2");
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
