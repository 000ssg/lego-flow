package ssg.legoflow.network.ldap.dn;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DnParser} and {@link DistinguishedName}.
 *
 * @since 1.0.0
 */
class DnParserTest {

    @Test
    void testParseSimpleDn() {
        DistinguishedName dn = DistinguishedName.parse("cn=John Doe,ou=People,dc=example,dc=com");
        assertThat(dn.rdns()).hasSize(4);
        assertThat(dn.rdns().get(0).type()).isEqualTo("cn");
        assertThat(dn.rdns().get(0).value()).isEqualTo("John Doe");
        assertThat(dn.rdns().get(1).type()).isEqualTo("ou");
        assertThat(dn.rdns().get(1).value()).isEqualTo("People");
        assertThat(dn.rdns().get(2).type()).isEqualTo("dc");
        assertThat(dn.rdns().get(2).value()).isEqualTo("example");
        assertThat(dn.rdns().get(3).type()).isEqualTo("dc");
        assertThat(dn.rdns().get(3).value()).isEqualTo("com");
    }

    @Test
    void testParseEmptyDn() {
        DistinguishedName dn = DistinguishedName.parse("");
        assertThat(dn.isEmpty()).isTrue();
        assertThat(dn.rdns()).isEmpty();
    }

    @Test
    void testParseDnWithEscapedComma() {
        DistinguishedName dn = DistinguishedName.parse("cn=Doe\\, John,dc=example,dc=com");
        assertThat(dn.rdns()).hasSize(3);
        assertThat(dn.rdns().get(0).value()).isEqualTo("Doe, John");
    }

    @Test
    void testParseDnWithEscapedSpecials() {
        DistinguishedName dn = DistinguishedName.parse("cn=Test\\+User,dc=example");
        assertThat(dn.rdns()).hasSize(2);
        assertThat(dn.rdns().get(0).value()).isEqualTo("Test+User");
    }

    @Test
    void testParseDnWithHexEscape() {
        // \2C is comma
        DistinguishedName dn = DistinguishedName.parse("cn=John\\2CDoe,dc=example");
        assertThat(dn.rdns()).hasSize(2);
        assertThat(dn.rdns().get(0).value()).isEqualTo("John,Doe");
    }

    @Test
    void testDnRoundTrip() {
        String original = "cn=John Doe,ou=People,dc=example,dc=com";
        DistinguishedName dn = DistinguishedName.parse(original);
        assertThat(dn.toString()).isEqualTo(original);
    }

    @Test
    void testDnParent() {
        DistinguishedName dn = DistinguishedName.parse("cn=John,ou=People,dc=example,dc=com");
        DistinguishedName parent = dn.parent();
        assertThat(parent.toString()).isEqualTo("ou=People,dc=example,dc=com");
    }

    @Test
    void testDnIsDescendant() {
        DistinguishedName child = DistinguishedName.parse("cn=John,ou=People,dc=example,dc=com");
        DistinguishedName base = DistinguishedName.parse("dc=example,dc=com");
        assertThat(child.isDescendantOf(base)).isTrue();
        assertThat(base.isDescendantOf(child)).isFalse();
    }

    @Test
    void testDnIsUnder() {
        DistinguishedName dn = DistinguishedName.parse("dc=example,dc=com");
        assertThat(dn.isUnder(dn)).isTrue();
    }

    @Test
    void testEscapeValue() {
        assertThat(DnParser.escapeValue("John, Doe")).isEqualTo("John\\, Doe");
        assertThat(DnParser.escapeValue(" leading")).isEqualTo("\\ leading");
        assertThat(DnParser.escapeValue("trailing ")).isEqualTo("trailing\\ ");
        assertThat(DnParser.escapeValue("#hash")).isEqualTo("\\#hash");
    }

    @Test
    void testParseNullDnThrows() {
        assertThatThrownBy(() -> DistinguishedName.parse(null))
                .isInstanceOf(DnParseException.class);
    }

    @Test
    void testMultiValuedRdn() {
        DistinguishedName dn = DistinguishedName.parse("cn=John+uid=jdoe,dc=example");
        assertThat(dn.rdns()).hasSize(2);
        assertThat(dn.rdns().get(0).components()).hasSize(2);
        assertThat(dn.rdns().get(0).components().get(0).type()).isEqualTo("cn");
        assertThat(dn.rdns().get(0).components().get(0).value()).isEqualTo("John");
        assertThat(dn.rdns().get(0).components().get(1).type()).isEqualTo("uid");
        assertThat(dn.rdns().get(0).components().get(1).value()).isEqualTo("jdoe");
    }

    @Test
    void testSingleComponentDn() {
        DistinguishedName dn = DistinguishedName.parse("dc=com");
        assertThat(dn.rdns()).hasSize(1);
        assertThat(dn.rdns().get(0).type()).isEqualTo("dc");
        assertThat(dn.rdns().get(0).value()).isEqualTo("com");
    }

    @Test
    void testEqualsIgnoreCase() {
        DistinguishedName dn1 = DistinguishedName.parse("CN=John,DC=Example,DC=Com");
        DistinguishedName dn2 = DistinguishedName.parse("cn=john,dc=example,dc=com");
        assertThat(dn1.equalsIgnoreCase(dn2)).isTrue();
    }
}
