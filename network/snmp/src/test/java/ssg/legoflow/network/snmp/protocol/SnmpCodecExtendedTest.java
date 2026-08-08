package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.asn1.Asn1BitString;
import ssg.legoflow.network.common.oid.ObjectIdentifier;

import java.net.InetAddress;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Extended tests for {@link SnmpCodec} covering individual SNMP value encoding/decoding,
 * unsigned byte helpers, and edge cases in encodeSnmpValue/decodeSnmpValue.
 */
class SnmpCodecExtendedTest {

    @Test
    void testEncodeDecodeInteger32() {
        var val = new SnmpValue.Integer32(42);
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(new SnmpValue.Integer32(42));
    }

    @Test
    void testEncodeDecodeInteger32Negative() {
        var val = new SnmpValue.Integer32(-100);
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(new SnmpValue.Integer32(-100));
    }

    @Test
    void testEncodeDecodeOctetString() {
        var val = new SnmpValue.OctetString("hello".getBytes());
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(new SnmpValue.OctetString("hello".getBytes()));
    }

    @Test
    void testEncodeDecodeOctetStringEmpty() {
        var val = new SnmpValue.OctetString(new byte[0]);
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(new SnmpValue.OctetString(new byte[0]));
    }

    @Test
    void testEncodeDecodeOid() {
        var val = new SnmpValue.Oid(ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isInstanceOf(SnmpValue.Oid.class);
    }

    @Test
    void testEncodeDecodeNull() {
        var val = SnmpValue.Null.INSTANCE;
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(SnmpValue.Null.INSTANCE);
    }

    @Test
    void testEncodeDecodeCounter32() {
        var val = new SnmpValue.Counter32(1_000_000);
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(new SnmpValue.Counter32(1_000_000));
    }

    @Test
    void testEncodeDecodeGauge32() {
        var val = new SnmpValue.Gauge32(65535);
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(new SnmpValue.Gauge32(65535));
    }

    @Test
    void testEncodeDecodeTimeTicks() {
        var val = new SnmpValue.TimeTicks(999_999_999L);
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(new SnmpValue.TimeTicks(999_999_999L));
    }

    @Test
    void testEncodeDecodeCounter64() {
        var val = new SnmpValue.Counter64(Long.MAX_VALUE);
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(new SnmpValue.Counter64(Long.MAX_VALUE));
    }

    @Test
    void testEncodeDecodeCounter64Small() {
        var val = new SnmpValue.Counter64(42);
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(new SnmpValue.Counter64(42));
    }

    @Test
    void testEncodeDecodeIpAddress() throws Exception {
        var addr = InetAddress.getByName("192.168.1.100");
        var val = new SnmpValue.IpAddress(addr.getAddress());
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isInstanceOf(SnmpValue.IpAddress.class);
    }

    @Test
    void testEncodeDecodeOpaque() {
        byte[] data = Base64.getDecoder().decode("dGVzdA==");
        var val = new SnmpValue.Opaque(data);
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isInstanceOf(SnmpValue.Opaque.class);
    }

    @Test
    void testEncodeDecodeNoSuchObject() {
        var val = SnmpValue.NoSuchObject.INSTANCE;
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(SnmpValue.NoSuchObject.INSTANCE);
    }

    @Test
    void testEncodeDecodeNoSuchInstance() {
        var val = SnmpValue.NoSuchInstance.INSTANCE;
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(SnmpValue.NoSuchInstance.INSTANCE);
    }

    @Test
    void testEncodeDecodeEndOfMibView() {
        var val = SnmpValue.EndOfMibView.INSTANCE;
        var encoded = SnmpCodec.encodeSnmpValue(val);
        var decoded = SnmpCodec.decodeSnmpValue(encoded);
        assertThat(decoded).isEqualTo(SnmpValue.EndOfMibView.INSTANCE);
    }

    @Test
    void testDecodeUnsupportedTypeThrows() {
        var unsupported = Asn1BitString.of(new byte[]{0x01});
        assertThatThrownBy(() -> SnmpCodec.decodeSnmpValue(unsupported))
                .isInstanceOf(SnmpCodecException.class)
                .hasMessageContaining("Unsupported SNMP value type");
    }

    @Test
    void testEncodeDecodeUsmSecurityParams() {
        var params = new UsmSecurityParameters(
                new byte[]{0x00, 0x01, 0x02},
                100, 200, "admin",
                new byte[12], new byte[16]);

        byte[] encoded = SnmpCodec.encodeUsmSecurityParams(params);
        assertThat(encoded).isNotEmpty();

        var decoded = SnmpCodec.decodeUsmSecurityParams(encoded);
        assertThat(decoded.userName()).isEqualTo("admin");
        assertThat(decoded.engineBoots()).isEqualTo(100);
        assertThat(decoded.engineTime()).isEqualTo(200);
    }

    @Test
    void testDecodeEmptyUsmSecurityParams() {
        var decoded = SnmpCodec.decodeUsmSecurityParams(new byte[0]);
        assertThat(decoded.userName()).isEmpty();
        assertThat(decoded.engineBoots()).isZero();
    }

    @Test
    void testDecodeNullUsmSecurityParams() {
        var decoded = SnmpCodec.decodeUsmSecurityParams(null);
        assertThat(decoded.userName()).isEmpty();
    }

    @Test
    void testEncodeDecodeSetRequestMessage() {
        VarBindList vbl = VarBindList.of(
                new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"),
                        new SnmpValue.OctetString("myRouter".getBytes())));
        SnmpPdu pdu = new SnmpPdu.SetRequest(99, 0, 0, vbl);
        ScopedPdu scopedPdu = new ScopedPdu(new byte[]{}, "", pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(200).msgMaxSize(65507)
                .securityLevel(SecurityLevel.AUTH_PRIV)
                .scopedPdu(scopedPdu).build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);
        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.SetRequest.class);
    }

    @Test
    void testEncodeDecodeGetBulkRequestMessage() {
        VarBindList vbl = VarBindList.of(VarBind.ofNull("1.3.6.1.2.1.1"));
        SnmpPdu pdu = new SnmpPdu.GetBulkRequest(77, 0, 10, vbl);
        ScopedPdu scopedPdu = new ScopedPdu(new byte[]{}, "", pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(201).msgMaxSize(65507)
                .securityLevel(SecurityLevel.NO_AUTH_NO_PRIV)
                .scopedPdu(scopedPdu).build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);
        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.GetBulkRequest.class);
    }

    @Test
    void testEncodeDecodeTrapV2Message() {
        VarBindList vbl = VarBindList.of(VarBind.ofNull("1.3.6.1.6.3.1.1.5.1"));
        SnmpPdu pdu = new SnmpPdu.TrapV2(88, 0, (int) (System.currentTimeMillis() % Integer.MAX_VALUE), vbl);
        ScopedPdu scopedPdu = new ScopedPdu(new byte[]{}, "", pdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(202).msgMaxSize(65507)
                .securityLevel(SecurityLevel.NO_AUTH_NO_PRIV)
                .scopedPdu(scopedPdu).build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);
        assertThat(decoded.scopedPdu().pdu()).isInstanceOf(SnmpPdu.TrapV2.class);
    }
}
