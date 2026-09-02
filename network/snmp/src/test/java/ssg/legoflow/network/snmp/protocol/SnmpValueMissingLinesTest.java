package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Targeted tests for the remaining uncovered lines in {@link SnmpValue}.
 */
class SnmpValueMissingLinesTest {

    // --- TimeTicks validation (1 missed line) ---
    @Test
    void testTimeTicksNegativeValidation() {
        assertThatThrownBy(() -> new SnmpValue.TimeTicks(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void testTimeTicksOverflowValidation() {
        assertThatThrownBy(() -> new SnmpValue.TimeTicks(0x100000000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTimeTicksValidZero() {
        var tt = new SnmpValue.TimeTicks(0);
        assertThat(tt.value()).isZero();
    }

    @Test
    void testTimeTicksValidMax() {
        var tt = new SnmpValue.TimeTicks(0xFFFFFFFFL);
        assertThat(tt.value()).isEqualTo(0xFFFFFFFFL);
    }

    // --- Gauge32 validation (1 missed line) ---
    @Test
    void testGauge32NegativeValidation() {
        assertThatThrownBy(() -> new SnmpValue.Gauge32(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void testGauge32OverflowValidation() {
        assertThatThrownBy(() -> new SnmpValue.Gauge32(0x100000000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Oid null validation (1 missed line) ---
    @Test
    void testOidNullThrows() {
        assertThatThrownBy(() -> new SnmpValue.Oid(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OID must not be null");
    }

    // --- OctetString null and toString (3 missed lines) ---
    @Test
    void testOctetStringNullThrows() {
        assertThatThrownBy(() -> new SnmpValue.OctetString(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testOctetStringToString() {
        var oct = new SnmpValue.OctetString(new byte[]{1, 2, 3});
        assertThat(oct.toString()).contains("length=3");
    }

    // --- IpAddress of() factory (7 missed lines) ---
    @Test
    void testIpAddressOfValid() {
        var ip = SnmpValue.IpAddress.of("192.168.0.1");
        assertThat(ip.address()).containsExactly((byte) 192, (byte) 168, 0, 1);
    }

    @Test
    void testIpAddressOfLocalhost() {
        var ip = SnmpValue.IpAddress.of("127.0.0.1");
        assertThat(ip.address()).containsExactly((byte) 127, 0, 0, 1);
    }

    @Test
    void testIpAddressOfInvalidParts() {
        assertThatThrownBy(() -> SnmpValue.IpAddress.of("192.168.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid IP address");
    }

    @Test
    void testIpAddressOfInvalidOctet() {
        assertThatThrownBy(() -> SnmpValue.IpAddress.of("192.168.1.999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid octet");
    }

    @Test
    void testIpAddressToString() {
        var ip = SnmpValue.IpAddress.of("10.0.0.1");
        assertThat(ip.toString()).contains("10.0.0.1");
    }

    @Test
    void testIpAddressNullThrows() {
        assertThatThrownBy(() -> new SnmpValue.IpAddress(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIpAddressWrongLengthThrows() {
        assertThatThrownBy(() -> new SnmpValue.IpAddress(new byte[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- IpAddress of() negative octet (2 missed lines) ---
    @Test
    void testIpAddressOfNegativeOctet() {
        assertThatThrownBy(() -> SnmpValue.IpAddress.of("192.168.1.-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- OctetString toString and equals (remaining lines) ---
    @Test
    void testOctetStringEquals() {
        var o1 = new SnmpValue.OctetString(new byte[]{1, 2, 3});
        var o2 = new SnmpValue.OctetString(new byte[]{1, 2, 3});
        assertThat(o1).isEqualTo(o2);
    }

    // --- Opaque defensive copy value() (remaining lines) ---
    @Test
    void testOpaqueDefensiveCopyOnValue() {
        var op = new SnmpValue.Opaque(new byte[]{1, 2});
        var val = op.value();
        val[0] = 99;
        assertThat(op.value()[0]).isEqualTo((byte) 1);
    }

    // --- IpAddress defensive copy on address() ---
    @Test
    void testIpAddressDefensiveCopyOnAddress() {
        var ip = new SnmpValue.IpAddress(new byte[]{1, 2, 3, 4});
        var addr = ip.address();
        addr[0] = 99;
        assertThat(ip.address()[0]).isEqualTo((byte) 1);
    }

    // --- IpAddress equals/hashCode ---
    @Test
    void testIpAddressEquals() {
        var i1 = SnmpValue.IpAddress.of("1.2.3.4");
        var i2 = SnmpValue.IpAddress.of("1.2.3.4");
        assertThat(i1).isEqualTo(i2);
        assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
    }
}
