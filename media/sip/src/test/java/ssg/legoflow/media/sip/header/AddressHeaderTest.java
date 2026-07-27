package ssg.legoflow.media.sip.header;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AddressHeader}.
 */
class AddressHeaderTest {

    @Test
    void testParseNameAddr() {
        var addr = AddressHeader.parse("\"Alice\" <sip:alice@atlanta.com>;tag=1928301774");
        assertThat(addr.displayName()).hasValue("Alice");
        assertThat(addr.uri().user()).hasValue("alice");
        assertThat(addr.uri().host()).isEqualTo("atlanta.com");
        assertThat(addr.tag()).hasValue("1928301774");
    }

    @Test
    void testParseNameAddrWithoutDisplayName() {
        var addr = AddressHeader.parse("<sip:alice@atlanta.com>;tag=abc");
        assertThat(addr.displayName()).isEmpty();
        assertThat(addr.uri().user()).hasValue("alice");
        assertThat(addr.tag()).hasValue("abc");
    }

    @Test
    void testParseNameAddrWithoutTag() {
        var addr = AddressHeader.parse("<sip:bob@biloxi.com>");
        assertThat(addr.displayName()).isEmpty();
        assertThat(addr.uri().user()).hasValue("bob");
        assertThat(addr.tag()).isEmpty();
    }

    @Test
    void testWithTag() {
        var addr = AddressHeader.parse("<sip:bob@biloxi.com>");
        var tagged = addr.withTag("newTag123");
        assertThat(tagged.tag()).hasValue("newTag123");
        // Original should be unchanged
        assertThat(addr.tag()).isEmpty();
    }

    @Test
    void testFormatNameAddr() {
        var addr = AddressHeader.parse("\"Alice\" <sip:alice@atlanta.com>;tag=abc");
        String formatted = addr.format();
        assertThat(formatted).contains("\"Alice\"");
        assertThat(formatted).contains("<sip:alice@atlanta.com>");
        assertThat(formatted).contains("tag=abc");
    }

    @Test
    void testFormatWithoutDisplayName() {
        var addr = AddressHeader.parse("<sip:bob@biloxi.com>;tag=def");
        String formatted = addr.format();
        assertThat(formatted).isEqualTo("<sip:bob@biloxi.com>;tag=def");
    }

    @Test
    void testParseWithPort() {
        var addr = AddressHeader.parse("<sip:alice@192.168.1.1:5060>;tag=xyz");
        assertThat(addr.uri().host()).isEqualTo("192.168.1.1");
        assertThat(addr.uri().port()).isEqualTo(5060);
        assertThat(addr.tag()).hasValue("xyz");
    }

    @Test
    void testParseDisplayNameWithoutQuotes() {
        var addr = AddressHeader.parse("Bob <sip:bob@biloxi.com>");
        assertThat(addr.displayName()).hasValue("Bob");
        assertThat(addr.uri().user()).hasValue("bob");
    }

    @Test
    void testNullValueThrows() {
        assertThatThrownBy(() -> AddressHeader.parse(null))
                .isInstanceOf(NullPointerException.class);
    }
}
