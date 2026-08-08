package ssg.legoflow.network.ldap.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LdapProtocolTest {
    @Test void testDerefAliases() {
        var values = DerefAliases.values();
        assertThat(values).isNotEmpty();
    }

    @Test void testLdapAttributeOf() {
        var attr = LdapAttribute.of("cn", "John");
        assertThat(attr.type()).isEqualTo("cn");
    }
}
