package ssg.legoflow.network.ldap.dn;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DistinguishedNameTest {

    @Test
    void testEmptyDn() {
        var dn = new DistinguishedName(java.util.List.of());
        assertThat(dn.isEmpty()).isTrue();
        assertThat(dn.rdns()).isEmpty();
    }

    @Test
    void testSingleRdn() {
        var dn = new DistinguishedName(java.util.List.of(Rdn.of("cn", "John Doe")));
        assertThat(dn.isEmpty()).isFalse();
        assertThat(dn.rdns()).hasSize(1);
    }

    @Test
    void testMultiRdn() {
        var dn = new DistinguishedName(java.util.List.of(
            Rdn.of("cn", "John Doe"),
            Rdn.of("ou", "People"),
            Rdn.of("dc", "example")
        ));
        assertThat(dn.rdns()).hasSize(3);
    }

    @Test
    void testParent() {
        var dn = new DistinguishedName(java.util.List.of(
            Rdn.of("cn", "John"), Rdn.of("ou", "People"), Rdn.of("dc", "example")
        ));
        var parent = dn.parent();
        assertThat(parent.rdns()).hasSize(2);
    }

    @Test
    void testParentOfSingleRdn() {
        var dn = new DistinguishedName(java.util.List.of(Rdn.of("cn", "John")));
        var parent = dn.parent();
        assertThat(parent.isEmpty()).isTrue();
    }

    @Test
    void testIsDescendantOf() {
        var base = new DistinguishedName(java.util.List.of(
            Rdn.of("dc", "example"), Rdn.of("dc", "com")
        ));
        var child = new DistinguishedName(java.util.List.of(
            Rdn.of("cn", "John"), Rdn.of("ou", "People"), 
            Rdn.of("dc", "example"), Rdn.of("dc", "com")
        ));
        assertThat(child.isDescendantOf(base)).isTrue();
    }

    @Test
    void testIsNotDescendantOf() {
        var base = new DistinguishedName(java.util.List.of(Rdn.of("dc", "other")));
        var dn = new DistinguishedName(java.util.List.of(
            Rdn.of("cn", "John"), Rdn.of("dc", "example")
        ));
        assertThat(dn.isDescendantOf(base)).isFalse();
    }

    @Test
    void testNullRdnsThrows() {
        assertThatThrownBy(() -> new DistinguishedName(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        var dn = new DistinguishedName(java.util.List.of(
            Rdn.of("cn", "John"), Rdn.of("dc", "example")
        ));
        String s = dn.toString();
        assertThat(s).isNotEmpty();
    }

    @Test
    void testEquality() {
        var dn1 = new DistinguishedName(java.util.List.of(Rdn.of("cn", "John")));
        var dn2 = new DistinguishedName(java.util.List.of(Rdn.of("cn", "John")));
        assertThat(dn1).isEqualTo(dn2);
    }

    @Test
    void testHashCode() {
        var dn1 = new DistinguishedName(java.util.List.of(Rdn.of("cn", "John")));
        var dn2 = new DistinguishedName(java.util.List.of(Rdn.of("cn", "John")));
        assertThat(dn1.hashCode()).isEqualTo(dn2.hashCode());
    }
}
