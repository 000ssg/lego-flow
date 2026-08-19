package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link SnmpCodec} BER encoding and decoding of SNMP messages.
 *
 * @since 0.1.0
 */
class SnmpCodecTest {

    // ── Message round-trip ──

    @Test
    void testEncodeDecodeGetRequestMessage() {
        VarBindList vbl = VarBindList.of(
                VarBind.ofNull("1.3.6.1.2.1.1.1.0"),
                VarBind.ofNull("1.3.6.1.2.1.1.3.0")
        );
        SnmpPdu pdu = new SnmpPdu.GetRequest(42, 0, 0, vbl);
        ScopedPdu scopedPdu = new ScopedPdu(new byte[]{0x01, 0x02}, "ctx", pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(100)
                .msgMaxSize(65507)
                .securityLevel(SecurityLevel.NO_AUTH_NO_PRIV)
                .scopedPdu(scopedPdu)
                .build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);

        assertThat(decoded.msgVersion()).isEqualTo(3);
        assertThat(decoded.msgId()).isEqualTo(100);
        assertThat(decoded.msgMaxSize()).isEqualTo(65507);
        assertThat(decoded.scopedPdu().contextName()).isEqualTo("ctx");
        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.GetRequest.class);

        SnmpPdu.GetRequest decodedPdu = (SnmpPdu.GetRequest) decoded.scopedPdu().pdu();
        assertThat(decodedPdu.requestId()).isEqualTo(42);
        assertThat(decodedPdu.varBindList().size()).isEqualTo(2);
    }

    @Test
    void testEncodeDecodeResponseMessage() {
        VarBindList vbl = VarBindList.of(
                new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"),
                        SnmpValue.OctetString.of("Test System")),
                new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.3.0"),
                        new SnmpValue.TimeTicks(12345))
        );
        SnmpPdu pdu = new SnmpPdu.Response(42, 0, 0, vbl);
        ScopedPdu scopedPdu = ScopedPdu.of(pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(101)
                .scopedPdu(scopedPdu)
                .build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);

        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.Response.class);
        SnmpPdu.Response resp = (SnmpPdu.Response) decoded.scopedPdu().pdu();
        assertThat(resp.requestId()).isEqualTo(42);
        assertThat(resp.errorStatus()).isEqualTo(0);
        assertThat(resp.varBindList().size()).isEqualTo(2);
    }

    @Test
    void testEncodeDecodeSetRequestMessage() {
        VarBindList vbl = VarBindList.of(
                new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"),
                        SnmpValue.OctetString.of("NewName"))
        );
        SnmpPdu pdu = new SnmpPdu.SetRequest(7, 0, 0, vbl);
        ScopedPdu scopedPdu = ScopedPdu.of(pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(200)
                .scopedPdu(scopedPdu)
                .build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);

        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.SetRequest.class);
    }

    @Test
    void testEncodeDecodeGetNextRequestMessage() {
        VarBindList vbl = VarBindList.of(VarBind.ofNull("1.3.6.1.2.1.1"));
        SnmpPdu pdu = new SnmpPdu.GetNextRequest(10, 0, 0, vbl);
        ScopedPdu scopedPdu = ScopedPdu.of(pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(300)
                .scopedPdu(scopedPdu)
                .build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);

        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.GetNextRequest.class);
        assertThat(decoded.scopedPdu().pdu().requestId()).isEqualTo(10);
    }

    @Test
    void testEncodeDecodeGetBulkRequestMessage() {
        VarBindList vbl = VarBindList.of(
                VarBind.ofNull("1.3.6.1.2.1.2.2.1.1"),
                VarBind.ofNull("1.3.6.1.2.1.2.2.1.2")
        );
        SnmpPdu pdu = new SnmpPdu.GetBulkRequest(20, 0, 10, vbl);
        ScopedPdu scopedPdu = ScopedPdu.of(pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(400)
                .scopedPdu(scopedPdu)
                .build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);

        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.GetBulkRequest.class);
        SnmpPdu.GetBulkRequest bulk = (SnmpPdu.GetBulkRequest) decoded.scopedPdu().pdu();
        assertThat(bulk.nonRepeaters()).isEqualTo(0);
        assertThat(bulk.maxRepetitions()).isEqualTo(10);
    }

    @Test
    void testEncodeDecodeTrapV2Message() {
        VarBindList vbl = VarBindList.of(
                new VarBind(SnmpOids.SYS_UP_TIME, new SnmpValue.TimeTicks(5000)),
                new VarBind(SnmpOids.SNMP_TRAP_OID, SnmpValue.Oid.of("1.3.6.1.6.3.1.1.5.1"))
        );
        SnmpPdu pdu = new SnmpPdu.TrapV2(0, 0, 0, vbl);
        ScopedPdu scopedPdu = ScopedPdu.of(pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(500)
                .scopedPdu(scopedPdu)
                .build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);

        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.TrapV2.class);
    }

    @Test
    void testEncodeDecodeInformRequestMessage() {
        VarBindList vbl = VarBindList.of(
                new VarBind(SnmpOids.SYS_UP_TIME, new SnmpValue.TimeTicks(1000)),
                new VarBind(SnmpOids.SNMP_TRAP_OID, SnmpValue.Oid.of("1.3.6.1.6.3.1.1.5.2"))
        );
        SnmpPdu pdu = new SnmpPdu.InformRequest(15, 0, 0, vbl);
        ScopedPdu scopedPdu = ScopedPdu.of(pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(600)
                .reportable(true)
                .scopedPdu(scopedPdu)
                .build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);

        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.InformRequest.class);
        assertThat(decoded.isReportable()).isTrue();
    }

    // ── VarBind value types ──

    @Test
    void testEncodeDecodeInteger32Value() {
        VarBind vb = new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.7.0"),
                new SnmpValue.Integer32(72));
        var seq = SnmpCodec.encodeVarBind(vb);
        VarBind decoded = SnmpCodec.decodeVarBind(seq);

        assertThat(decoded.value()).isInstanceOf(SnmpValue.Integer32.class);
        assertThat(((SnmpValue.Integer32) decoded.value()).value()).isEqualTo(72);
    }

    @Test
    void testEncodeDecodeNegativeInteger32Value() {
        VarBind vb = new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.7.0"),
                new SnmpValue.Integer32(-42));
        var seq = SnmpCodec.encodeVarBind(vb);
        VarBind decoded = SnmpCodec.decodeVarBind(seq);

        assertThat(((SnmpValue.Integer32) decoded.value()).value()).isEqualTo(-42);
    }

    @Test
    void testEncodeDecodeOctetStringValue() {
        VarBind vb = new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"),
                SnmpValue.OctetString.of("Hello SNMP"));
        var seq = SnmpCodec.encodeVarBind(vb);
        VarBind decoded = SnmpCodec.decodeVarBind(seq);

        assertThat(decoded.value()).isInstanceOf(SnmpValue.OctetString.class);
        assertThat(((SnmpValue.OctetString) decoded.value()).asString()).isEqualTo("Hello SNMP");
    }

    @Test
    void testEncodeDecodeOidValue() {
        VarBind vb = new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.2.0"),
                SnmpValue.Oid.of("1.3.6.1.4.1.99"));
        var seq = SnmpCodec.encodeVarBind(vb);
        VarBind decoded = SnmpCodec.decodeVarBind(seq);

        assertThat(decoded.value()).isInstanceOf(SnmpValue.Oid.class);
        assertThat(((SnmpValue.Oid) decoded.value()).value().toDottedString())
                .isEqualTo("1.3.6.1.4.1.99");
    }

    @Test
    void testEncodeDecodeNullValue() {
        VarBind vb = VarBind.ofNull("1.3.6.1.2.1.1.1.0");
        var seq = SnmpCodec.encodeVarBind(vb);
        VarBind decoded = SnmpCodec.decodeVarBind(seq);

        assertThat(decoded.value()).isInstanceOf(SnmpValue.Null.class);
    }

    // ── USM Security Parameters ──

    @Test
    void testEncodeDecodeUsmSecurityParams() {
        UsmSecurityParameters params = new UsmSecurityParameters(
                new byte[]{0x01, 0x02, 0x03, 0x04},
                5, 100, "admin",
                new byte[12], new byte[8]);

        byte[] encoded = SnmpCodec.encodeUsmSecurityParams(params);
        UsmSecurityParameters decoded = SnmpCodec.decodeUsmSecurityParams(encoded);

        assertThat(decoded.engineId()).containsExactly(0x01, 0x02, 0x03, 0x04);
        assertThat(decoded.engineBoots()).isEqualTo(5);
        assertThat(decoded.engineTime()).isEqualTo(100);
        assertThat(decoded.userName()).isEqualTo("admin");
        assertThat(decoded.authParams()).hasSize(12);
        assertThat(decoded.privParams()).hasSize(8);
    }

    @Test
    void testDecodeEmptyUsmSecurityParams() {
        UsmSecurityParameters decoded = SnmpCodec.decodeUsmSecurityParams(new byte[0]);
        assertThat(decoded.userName()).isEmpty();
        assertThat(decoded.engineBoots()).isEqualTo(0);
    }

    // ── Scoped PDU ──

    @Test
    void testScopedPduRoundTrip() {
        SnmpPdu pdu = new SnmpPdu.GetRequest(1, 0, 0,
                VarBindList.of(VarBind.ofNull("1.3.6.1.2.1.1.1.0")));
        ScopedPdu scopedPdu = new ScopedPdu(
                new byte[]{(byte) 0x80, 0x00, 0x01}, "myContext", pdu);

        byte[] encoded = SnmpCodec.encodeScopedPduBytes(scopedPdu);
        ScopedPdu decoded = SnmpCodec.decodeScopedPduBytes(encoded);

        assertThat(decoded.contextEngineId()).containsExactly((byte) 0x80, 0x00, 0x01);
        assertThat(decoded.contextName()).isEqualTo("myContext");
        assertThat(decoded.pdu()).isInstanceOf(SnmpPdu.GetRequest.class);
    }

    // ── Security level flags ──

    @Test
    void testSecurityLevelInMessage() {
        SnmpPdu pdu = new SnmpPdu.GetRequest(1, 0, 0, VarBindList.empty());
        ScopedPdu scopedPdu = ScopedPdu.of(pdu);

        SnmpMessage msg = SnmpMessage.builder()
                .msgId(1)
                .securityLevel(SecurityLevel.AUTH_PRIV)
                .reportable(true)
                .scopedPdu(scopedPdu)
                .build();

        assertThat(msg.securityLevel()).isEqualTo(SecurityLevel.AUTH_PRIV);
        assertThat(msg.isReportable()).isTrue();
        assertThat(msg.msgFlags() & 0x03).isEqualTo(0x03);
        assertThat(msg.msgFlags() & 0x04).isEqualTo(0x04);
    }

    // ── Error handling ──

    @Test
    void testDecodeMalformedMessageThrows() {
        assertThatThrownBy(() -> SnmpCodec.decodeMessage(new byte[]{0x00, 0x01}))
                .isInstanceOf(SnmpCodecException.class);
    }

    @Test
    void testEncodeDecodeEmptyVarBindList() {
        VarBindList empty = VarBindList.empty();
        var seq = SnmpCodec.encodeVarBindList(empty);
        VarBindList decoded = SnmpCodec.decodeVarBindList(seq);
        assertThat(decoded.isEmpty()).isTrue();
    }
}
