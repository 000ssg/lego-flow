package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link VarBind}.
 *
 * @since 0.1.0
 */
class VarBindTest {

    @Test
    void testOfNullWithOid() {
        var oid = ObjectIdentifier.parse("1.3.6.1.2.1.1.1");
        var vb = VarBind.ofNull(oid);
        assertThat(vb.oid()).isEqualTo(oid);
        assertThat(vb.value()).isInstanceOf(SnmpValue.Null.class);
    }

    @Test
    void testOfNullWithString() {
        var vb = VarBind.ofNull("1.3.6.1.2.1.1.2");
        assertThat(vb.oid().toDottedString()).isEqualTo("1.3.6.1.2.1.1.2");
        assertThat(vb.value()).isInstanceOf(SnmpValue.Null.class);
    }

    @Test
    void testConstructorNullOidThrows() {
        assertThatThrownBy(() -> new VarBind(null, SnmpValue.Null.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OID must not be null");
    }

    @Test
    void testConstructorNullValueThrows() {
        var oid = ObjectIdentifier.parse("1.3.6.1.2.1.1.1");
        assertThatThrownBy(() -> new VarBind(oid, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value must not be null");
    }

    @Test
    void testWithValue() {
        var oid = ObjectIdentifier.parse("1.3.6.1.2.1.1.1");
        var vb = new VarBind(oid, new SnmpValue.Integer32(42));
        assertThat(vb.oid()).isEqualTo(oid);
        assertThat(vb.value()).isInstanceOf(SnmpValue.Integer32.class);
    }
}
