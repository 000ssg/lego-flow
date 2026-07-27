package ssg.legoflow.email.common.builder;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.common.mime.ContentTransferEncoding;
import ssg.legoflow.email.common.mime.MimePart;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MimePartBuilder}.
 */
class MimePartBuilderTest {

    @Test
    void testBuildTextPlainPart() {
        MimePart part = MimePartBuilder.create()
                .textPlain(StandardCharsets.UTF_8)
                .content("Hello, World!")
                .build();

        assertThat(part.contentType().mediaType()).isEqualTo("text/plain");
        assertThat(part.isText()).isTrue();
    }

    @Test
    void testBuildTextHtmlPart() {
        MimePart part = MimePartBuilder.create()
                .textHtml(StandardCharsets.UTF_8)
                .content("<html>Hello</html>")
                .build();

        assertThat(part.contentType().mediaType()).isEqualTo("text/html");
    }

    @Test
    void testBuildAttachment() {
        MimePart part = MimePartBuilder.create()
                .contentType("application/pdf")
                .attachment("document.pdf")
                .transferEncoding(ContentTransferEncoding.BASE64)
                .content(new byte[]{1, 2, 3})
                .build();

        assertThat(part.isAttachment()).isTrue();
        assertThat(part.filename()).isEqualTo("document.pdf");
    }

    @Test
    void testBuildInlinePart() {
        MimePart part = MimePartBuilder.create()
                .contentType("image/png")
                .inline()
                .contentId("image1")
                .content(new byte[]{0, 1, 2})
                .build();

        assertThat(part.isInline()).isTrue();
        assertThat(part.headers().get("Content-ID")).isEqualTo("<image1>");
    }

    @Test
    void testBuildWithTransferEncoding() {
        MimePart part = MimePartBuilder.create()
                .contentType("text/plain")
                .transferEncoding(ContentTransferEncoding.QUOTED_PRINTABLE)
                .content("Hello")
                .build();

        assertThat(part.contentTransferEncoding()).isEqualTo(ContentTransferEncoding.QUOTED_PRINTABLE);
    }

    @Test
    void testBuildWithEncodedContent() {
        MimePart part = MimePartBuilder.create()
                .contentType("text/plain")
                .transferEncoding(ContentTransferEncoding.BASE64)
                .content("Hello, World!")
                .encodeContent()
                .build();

        String raw = new String(part.rawContent(), StandardCharsets.US_ASCII);
        assertThat(raw).isEqualTo("SGVsbG8sIFdvcmxkIQ==");
        assertThat(part.decodedContentAsString()).isEqualTo("Hello, World!");
    }

    @Test
    void testBuildWithCustomHeader() {
        MimePart part = MimePartBuilder.create()
                .contentType("text/plain")
                .header("X-Custom", "value")
                .content("Hello")
                .build();

        assertThat(part.headers().get("X-Custom")).isEqualTo("value");
    }

    @Test
    void testBuildWithByteContent() {
        byte[] data = {1, 2, 3, 4, 5};
        MimePart part = MimePartBuilder.create()
                .contentType("application/octet-stream")
                .content(data)
                .build();

        assertThat(part.rawContent()).isEqualTo(data);
    }

    @Test
    void testBuildWithCharsetContent() {
        MimePart part = MimePartBuilder.create()
                .textPlain(StandardCharsets.ISO_8859_1)
                .content("café", StandardCharsets.ISO_8859_1)
                .build();

        assertThat(part.rawContent()).isEqualTo("café".getBytes(StandardCharsets.ISO_8859_1));
    }
}
