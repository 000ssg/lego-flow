package ssg.legoflow.media.sip.uri;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.protocol.SipUri;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SipUri}.
 */
class SipUriTest {

    @Test
    void testParseSipUri() {
        var uri = SipUri.parse("sip:alice@atlanta.com");
        assertThat(uri.scheme()).isEqualTo("sip");
        assertThat(uri.user()).hasValue("alice");
        assertThat(uri.host()).isEqualTo("atlanta.com");
        assertThat(uri.port()).isEqualTo(-1);
    }

    @Test
    void testParseSipUriWithPort() {
        var uri = SipUri.parse("sip:alice@atlanta.com:5060");
        assertThat(uri.user()).hasValue("alice");
        assertThat(uri.host()).isEqualTo("atlanta.com");
        assertThat(uri.port()).isEqualTo(5060);
    }

    @Test
    void testParseSipsUri() {
        var uri = SipUri.parse("sips:bob@biloxi.com");
        assertThat(uri.scheme()).isEqualTo("sips");
        assertThat(uri.isSecure()).isTrue();
        assertThat(uri.user()).hasValue("bob");
        assertThat(uri.host()).isEqualTo("biloxi.com");
    }

    @Test
    void testParseSipUriWithPassword() {
        var uri = SipUri.parse("sip:alice:secret@atlanta.com");
        assertThat(uri.user()).hasValue("alice");
        assertThat(uri.password()).hasValue("secret");
    }

    @Test
    void testParseSipUriWithParameters() {
        var uri = SipUri.parse("sip:alice@atlanta.com;transport=tcp;user=phone");
        assertThat(uri.transport()).hasValue("tcp");
        assertThat(uri.parameter("user")).hasValue("phone");
    }

    @Test
    void testParseSipUriWithHeaders() {
        var uri = SipUri.parse("sip:alice@atlanta.com?subject=meeting&priority=urgent");
        assertThat(uri.headers()).containsEntry("subject", "meeting");
        assertThat(uri.headers()).containsEntry("priority", "urgent");
    }

    @Test
    void testParseTelUri() {
        var uri = SipUri.parse("tel:+1-201-555-0123");
        assertThat(uri.scheme()).isEqualTo("tel");
        assertThat(uri.isTelUri()).isTrue();
        assertThat(uri.user()).hasValue("+1-201-555-0123");
    }

    @Test
    void testParseSipUriHostOnly() {
        var uri = SipUri.parse("sip:atlanta.com");
        assertThat(uri.user()).isEmpty();
        assertThat(uri.host()).isEqualTo("atlanta.com");
    }

    @Test
    void testEffectivePortSip() {
        var uri = SipUri.parse("sip:alice@atlanta.com");
        assertThat(uri.effectivePort()).isEqualTo(5060);
    }

    @Test
    void testEffectivePortSips() {
        var uri = SipUri.parse("sips:alice@atlanta.com");
        assertThat(uri.effectivePort()).isEqualTo(5061);
    }

    @Test
    void testEffectivePortExplicit() {
        var uri = SipUri.parse("sip:alice@atlanta.com:5080");
        assertThat(uri.effectivePort()).isEqualTo(5080);
    }

    @Test
    void testFormatSipUri() {
        var uri = SipUri.parse("sip:alice@atlanta.com:5060");
        assertThat(uri.format()).isEqualTo("sip:alice@atlanta.com:5060");
    }

    @Test
    void testFormatSipUriWithParams() {
        var uri = SipUri.parse("sip:alice@atlanta.com;transport=tcp");
        String formatted = uri.format();
        assertThat(formatted).contains("sip:alice@atlanta.com");
        assertThat(formatted).contains("transport=tcp");
    }

    @Test
    void testFormatTelUri() {
        var uri = SipUri.parse("tel:+1-201-555-0123");
        assertThat(uri.format()).isEqualTo("tel:+1-201-555-0123");
    }

    @Test
    void testEquality() {
        var uri1 = SipUri.parse("sip:alice@atlanta.com");
        var uri2 = SipUri.parse("sip:alice@ATLANTA.COM");
        assertThat(uri1).isEqualTo(uri2);
    }

    @Test
    void testInequalityDifferentUser() {
        var uri1 = SipUri.parse("sip:alice@atlanta.com");
        var uri2 = SipUri.parse("sip:bob@atlanta.com");
        assertThat(uri1).isNotEqualTo(uri2);
    }

    @Test
    void testInvalidUriMissingScheme() {
        assertThatThrownBy(() -> SipUri.parse("alice@atlanta.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidUriUnsupportedScheme() {
        assertThatThrownBy(() -> SipUri.parse("http:alice@atlanta.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseSipUriWithPortAndParams() {
        var uri = SipUri.parse("sip:alice@atlanta.com:5060;transport=udp");
        assertThat(uri.host()).isEqualTo("atlanta.com");
        assertThat(uri.port()).isEqualTo(5060);
        assertThat(uri.transport()).hasValue("udp");
    }

    @Test
    void testNullUriThrows() {
        assertThatThrownBy(() -> SipUri.parse(null))
                .isInstanceOf(NullPointerException.class);
    }
}
