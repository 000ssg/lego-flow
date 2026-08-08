package ssg.legoflow.network.ldap.dn;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RdnTest {

    @Test
    void testSingleComponent() {
        var rdn = Rdn.of("cn", "John Doe");
        assertThat(rdn.type()).isEqualTo("cn");
        assertThat(rdn.value()).isEqualTo("John Doe");
    }

    @Test
    void testMultipleComponents() {
        var comp1 = new Rdn.RdnComponent("cn", "John");
        var comp2 = new Rdn.RdnComponent("uid", "jdoe");
        var rdn = new Rdn(java.util.List.of(comp1, comp2));
        assertThat(rdn.components()).hasSize(2);
    }

    @Test
    void testNullComponentsThrows() {
        assertThatThrownBy(() -> new Rdn(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEmptyComponentsThrows() {
        assertThatThrownBy(() -> new Rdn(java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCaseInsensitiveEquals() {
        var rdn1 = Rdn.of("CN", "John Doe");
        var rdn2 = Rdn.of("cn", "john doe");
        assertThat(rdn1.equalsIgnoreCase(rdn2)).isTrue();
    }

    @Test
    void testToString() {
        var rdn = Rdn.of("ou", "Engineering");
        assertThat(rdn.toString()).isEqualTo("ou=Engineering");
    }

    @Test
    void testComponentEquality() {
        var c1 = new Rdn.RdnComponent("cn", "John");
        var c2 = new Rdn.RdnComponent("cn", "John");
        assertThat(c1).isEqualTo(c2);
    }
}
