package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link VarBindList} and its builder.
 *
 * @since 0.1.0
 */
class VarBindListTest {

    @Test
    void testEmpty() {
        var list = VarBindList.empty();
        assertThat(list.size()).isZero();
        assertThat(list.isEmpty()).isTrue();
    }

    @Test
    void testOfVarargs() {
        var vb1 = VarBind.ofNull("1.3.6.1.2.1");
        var vb2 = VarBind.ofNull("1.3.6.1.2.1.1");
        var list = VarBindList.of(vb1, vb2);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.isEmpty()).isFalse();
        assertThat(list.get(0)).isSameAs(vb1);
    }

    @Test
    void testOfList() {
        var list = VarBindList.of(java.util.List.of(VarBind.ofNull("1.3.6.1.2.1")));
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    void testBuilderAddNullOid() {
        var oid = ObjectIdentifier.parse("1.3.6.1.2.1.1.1");
        var list = VarBindList.builder().addNull(oid).build();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).value()).isInstanceOf(SnmpValue.Null.class);
    }

    @Test
    void testBuilderAddNullString() {
        var list = VarBindList.builder().addNull("1.3.6.1.2.1.1.2").build();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).oid().toDottedString()).isEqualTo("1.3.6.1.2.1.1.2");
    }

    @Test
    void testBuilderAddVarBind() {
        var vb = VarBind.ofNull("1.3.6.1.4.1");
        var list = VarBindList.builder().add(vb).build();
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    void testBuilderMultipleBindings() {
        var list = VarBindList.builder()
                .addNull("1.3.6.1.2.1.1.1")
                .addNull("1.3.6.1.2.1.1.2")
                .add(VarBind.ofNull("1.3.6.1.2.1.1.3"))
                .build();
        assertThat(list.size()).isEqualTo(3);
    }

    @Test
    void testConstructorNullThrows() {
        assertThatThrownBy(() -> new VarBindList(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void testDefensiveCopy() {
        var binds = new java.util.ArrayList<VarBind>();
        binds.add(VarBind.ofNull("1.3.6.1.2.1"));
        var list = VarBindList.of(binds);
        binds.clear();
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    void testIterator() {
        var list = VarBindList.builder()
                .addNull("1.3.6.1.2.1")
                .addNull("1.3.6.1.2.1.1")
                .build();
        int count = 0; for (var vb : list) { count++; }
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testBuilderEmptyBuilds() {
        var list = VarBindList.builder().build();
        assertThat(list.isEmpty()).isTrue();
    }
}
