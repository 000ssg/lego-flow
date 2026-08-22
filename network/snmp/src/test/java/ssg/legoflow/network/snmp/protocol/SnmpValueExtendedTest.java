package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import static org.assertj.core.api.Assertions.*;
class SnmpValueExtendedTest {

    @Test
    void testCounter64() {
        var value = new SnmpValue.Counter64(999);
        assertThat(value.value()).isEqualTo(999);
    }

    @Test
    void testCounter64Zero() {
        var value = new SnmpValue.Counter64(0);
        assertThat(value.value()).isZero();
    }

    @Test
    void testCounter64MaxValue() {
        var max = new SnmpValue.Counter64(Long.MAX_VALUE);
        assertThat(max.value()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void testOpaque() {
        byte[] data = {(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF};
        var value = new SnmpValue.Opaque(data);
        assertThat(value.value()).containsExactly((byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF);
    }

    @Test
    void testOpaqueNullThrows() {
        assertThatThrownBy(() -> new SnmpValue.Opaque(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testOpaqueEmptyData() {
        var value = new SnmpValue.Opaque(new byte[0]);
        assertThat(value.value()).isEmpty();
    }

    @Test
    void testOpaqueLargeData() {
        byte[] large = new byte[1024];
        java.util.Arrays.fill(large, (byte)42);
        var value = new SnmpValue.Opaque(large);
        assertThat(value.value()).hasSize(1024);
    }

    @Test
    void testNull() {
        var n1 = new SnmpValue.Null();
        var n2 = new SnmpValue.Null();
        assertThat(n1).isEqualTo(n2);
    }

    @Test
    void testNoSuchObject() {
        var e1 = new SnmpValue.NoSuchObject();
        var e2 = new SnmpValue.NoSuchObject();
        assertThat(e1).isEqualTo(e2);
    }

    @Test
    void testNoSuchInstance() {
        var e1 = new SnmpValue.NoSuchInstance();
        var e2 = new SnmpValue.NoSuchInstance();
        assertThat(e1).isEqualTo(e2);
    }

    @Test
    void testEndOfMibView() {
        var e1 = new SnmpValue.EndOfMibView();
        var e2 = new SnmpValue.EndOfMibView();
        assertThat(e1).isEqualTo(e2);
    }

    @Test
    void testAllTypesImplementSnmpValue() {
        ObjectIdentifier oid = ObjectIdentifier.of(1, 3, 6);
        assertThat(new SnmpValue.Counter64(200L)).isInstanceOf(SnmpValue.class);
        assertThat(new SnmpValue.Opaque(new byte[]{1})).isInstanceOf(SnmpValue.class);
        assertThat(new SnmpValue.Null()).isInstanceOf(SnmpValue.class);
        assertThat(new SnmpValue.NoSuchObject()).isInstanceOf(SnmpValue.class);
        assertThat(new SnmpValue.NoSuchInstance()).isInstanceOf(SnmpValue.class);
        assertThat(new SnmpValue.EndOfMibView()).isInstanceOf(SnmpValue.class);
        assertThat(new SnmpValue.Oid(oid)).isInstanceOf(SnmpValue.class);
    }

    @Test
    void testIpAddress() {
        byte[] addr = {127, 0, 0, 1};
        var value = new SnmpValue.IpAddress(addr);
        assertThat(value.address()).isEqualTo(addr);
    }
}
