package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SnmpValue} data types.
 *
 * @since 0.1.0
 */
class SnmpValueTest {

    @Test
    void testInteger32() {
        var value = new SnmpValue.Integer32(42);
        assertThat(value.value()).isEqualTo(42);
    }

    @Test
    void testCounter32() {
        var value = new SnmpValue.Counter32(4294967295L);
        assertThat(value.value()).isEqualTo(4294967295L);
    }

    @Test
    void testCounter32Validation() {
        assertThatThrownBy(() -> new SnmpValue.Counter32(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnmpValue.Counter32(4294967296L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGauge32() {
        var value = new SnmpValue.Gauge32(100);
        assertThat(value.value()).isEqualTo(100);
    }

    @Test
    void testTimeTicks() {
        var value = new SnmpValue.TimeTicks(12345);
        assertThat(value.value()).isEqualTo(12345);
    }

    @Test
    void testOctetString() {
        var value = SnmpValue.OctetString.of("Hello");
        assertThat(value.asString()).isEqualTo("Hello");
    }

    @Test
    void testOctetStringDefensiveCopy() {
        byte[] data = {1, 2, 3};
        var value = new SnmpValue.OctetString(data);
        data[0] = 99;
        assertThat(value.value()[0]).isEqualTo((byte) 1);
    }

    @Test
    void testOidValue() {
        var value = SnmpValue.Oid.of("1.3.6.1.2.1");
        assertThat(value.value().toDottedString()).isEqualTo("1.3.6.1.2.1");
    }

    @Test
    void testIpAddress() {
        var value = SnmpValue.IpAddress.of("192.168.1.1");
        assertThat(value.address()).containsExactly((byte) 192, (byte) 168, 1, 1);
    }

    @Test
    void testIpAddressValidation() {
        assertThatThrownBy(() -> new SnmpValue.IpAddress(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullSingleton() {
        assertThat(SnmpValue.Null.INSTANCE).isSameAs(SnmpValue.Null.INSTANCE);
    }

    @Test
    void testNoSuchObjectSingleton() {
        assertThat(SnmpValue.NoSuchObject.INSTANCE).isSameAs(SnmpValue.NoSuchObject.INSTANCE);
    }

    @Test
    void testEndOfMibViewSingleton() {
        assertThat(SnmpValue.EndOfMibView.INSTANCE).isSameAs(SnmpValue.EndOfMibView.INSTANCE);
    }
}
