package ssg.legoflow.email.common.mime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ContentType}.
 */
class ContentTypeTest {

    @Test
    void testParseSimple() {
        ContentType ct = ContentType.parse("text/plain");
        assertThat(ct.type()).isEqualTo("text");
        assertThat(ct.subtype()).isEqualTo("plain");
        assertThat(ct.mediaType()).isEqualTo("text/plain");
    }

    @Test
    void testParseWithCharset() {
        ContentType ct = ContentType.parse("text/plain; charset=utf-8");
        assertThat(ct.type()).isEqualTo("text");
        assertThat(ct.subtype()).isEqualTo("plain");
        assertThat(ct.parameter("charset")).isEqualTo("utf-8");
        assertThat(ct.charset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void testParseWithBoundary() {
        ContentType ct = ContentType.parse(
                "multipart/mixed; boundary=\"----=_Part_123\"");
        assertThat(ct.type()).isEqualTo("multipart");
        assertThat(ct.subtype()).isEqualTo("mixed");
        assertThat(ct.boundary()).isEqualTo("----=_Part_123");
    }

    @Test
    void testParseWithNameParameter() {
        ContentType ct = ContentType.parse(
                "application/pdf; name=\"document.pdf\"");
        assertThat(ct.name()).isEqualTo("document.pdf");
    }

    @Test
    void testParseMultipleParameters() {
        ContentType ct = ContentType.parse(
                "text/html; charset=UTF-8; name=\"page.html\"");
        assertThat(ct.parameter("charset")).isEqualTo("UTF-8");
        assertThat(ct.parameter("name")).isEqualTo("page.html");
    }

    @Test
    void testParseCaseInsensitive() {
        ContentType ct = ContentType.parse("TEXT/HTML");
        assertThat(ct.type()).isEqualTo("text");
        assertThat(ct.subtype()).isEqualTo("html");
    }

    @Test
    void testParseNullReturnsDefault() {
        assertThat(ContentType.parse(null).mediaType()).isEqualTo("text/plain");
    }

    @Test
    void testParseMissingSubtypeThrows() {
        assertThatThrownBy(() -> ContentType.parse("text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIsText() {
        assertThat(ContentType.parse("text/plain").isText()).isTrue();
        assertThat(ContentType.parse("text/html").isText()).isTrue();
        assertThat(ContentType.parse("image/png").isText()).isFalse();
    }

    @Test
    void testIsMultipart() {
        assertThat(ContentType.parse("multipart/mixed").isMultipart()).isTrue();
        assertThat(ContentType.parse("text/plain").isMultipart()).isFalse();
    }

    @Test
    void testIsMessage() {
        assertThat(ContentType.parse("message/rfc822").isMessage()).isTrue();
        assertThat(ContentType.parse("text/plain").isMessage()).isFalse();
    }

    @Test
    void testDefaultCharsetForText() {
        ContentType ct = ContentType.parse("text/plain");
        assertThat(ct.charset()).isEqualTo(StandardCharsets.US_ASCII);
    }

    @Test
    void testDefaultCharsetForNonText() {
        ContentType ct = ContentType.parse("application/octet-stream");
        assertThat(ct.charset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void testWithParameter() {
        ContentType ct = ContentType.parse("text/plain");
        ContentType withCharset = ct.withParameter("charset", "utf-8");
        assertThat(withCharset.parameter("charset")).isEqualTo("utf-8");
        // Original unchanged
        assertThat(ct.parameter("charset")).isNull();
    }

    @Test
    void testToHeaderValue() {
        ContentType ct = new ContentType("text", "plain",
                java.util.Map.of("charset", "utf-8"));
        assertThat(ct.toHeaderValue()).isEqualTo("text/plain; charset=utf-8");
    }

    @Test
    void testEquality() {
        ContentType a = ContentType.parse("text/plain; charset=utf-8");
        ContentType b = ContentType.parse("text/plain; charset=utf-8");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void testConstants() {
        assertThat(ContentType.TEXT_PLAIN.mediaType()).isEqualTo("text/plain");
        assertThat(ContentType.TEXT_HTML.mediaType()).isEqualTo("text/html");
        assertThat(ContentType.MESSAGE_RFC822.mediaType()).isEqualTo("message/rfc822");
        assertThat(ContentType.APPLICATION_OCTET_STREAM.mediaType()).isEqualTo("application/octet-stream");
    }
}
