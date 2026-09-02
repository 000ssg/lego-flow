package ssg.legoflow.email.common.address;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link EmailAddress}.
 */
class EmailAddressTest {

    @Test
    void testParseSimple() {
        EmailAddress addr = EmailAddress.parse("user@example.com");
        assertThat(addr.localPart()).isEqualTo("user");
        assertThat(addr.domain()).isEqualTo("example.com");
        assertThat(addr.address()).isEqualTo("user@example.com");
    }

    @Test
    void testParseWithAngleBrackets() {
        EmailAddress addr = EmailAddress.parse("<user@example.com>");
        assertThat(addr.localPart()).isEqualTo("user");
        assertThat(addr.domain()).isEqualTo("example.com");
    }

    @Test
    void testParseWithWhitespace() {
        EmailAddress addr = EmailAddress.parse("  user@example.com  ");
        assertThat(addr.localPart()).isEqualTo("user");
        assertThat(addr.domain()).isEqualTo("example.com");
    }

    @Test
    void testParseWithDots() {
        EmailAddress addr = EmailAddress.parse("first.last@sub.example.com");
        assertThat(addr.localPart()).isEqualTo("first.last");
        assertThat(addr.domain()).isEqualTo("sub.example.com");
    }

    @Test
    void testParseWithPlus() {
        EmailAddress addr = EmailAddress.parse("user+tag@example.com");
        assertThat(addr.localPart()).isEqualTo("user+tag");
    }

    @Test
    void testParseInvalidNoAtSign() {
        assertThatThrownBy(() -> EmailAddress.parse("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseInvalidEmpty() {
        assertThatThrownBy(() -> EmailAddress.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseInvalidNull() {
        assertThatThrownBy(() -> EmailAddress.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEqualsCaseInsensitiveDomain() {
        EmailAddress a = EmailAddress.parse("user@Example.COM");
        EmailAddress b = EmailAddress.parse("user@example.com");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void testEqualsCaseSensitiveLocalPart() {
        EmailAddress a = EmailAddress.parse("User@example.com");
        EmailAddress b = EmailAddress.parse("user@example.com");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void testToString() {
        EmailAddress addr = new EmailAddress("user", "example.com");
        assertThat(addr.toString()).isEqualTo("user@example.com");
    }

    @Test
    void testConstructorValidation() {
        assertThatThrownBy(() -> new EmailAddress(null, "example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress("user", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress("", "example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
