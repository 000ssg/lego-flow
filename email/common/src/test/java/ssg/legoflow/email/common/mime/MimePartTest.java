package ssg.legoflow.email.common.mime;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.common.encoding.Base64Codec;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MimePart}.
 */
class MimePartTest {

    @Test
    void testSimpleTextPart() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Type", "text/plain; charset=utf-8");
        MimePart part = new MimePart(headers, "Hello".getBytes(StandardCharsets.UTF_8));

        assertThat(part.isText()).isTrue();
        assertThat(part.isAttachment()).isFalse();
        assertThat(part.isInline()).isTrue();
        assertThat(part.decodedContentAsString()).isEqualTo("Hello");
    }

    @Test
    void testBase64DecodedContent() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Type", "text/plain; charset=utf-8");
        headers.add("Content-Transfer-Encoding", "base64");
        String encoded = Base64Codec.encode("Decoded!".getBytes(StandardCharsets.UTF_8));
        MimePart part = new MimePart(headers, encoded.getBytes(StandardCharsets.US_ASCII));

        assertThat(part.decodedContentAsString()).isEqualTo("Decoded!");
    }

    @Test
    void testQuotedPrintableDecodedContent() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Type", "text/plain; charset=utf-8");
        headers.add("Content-Transfer-Encoding", "quoted-printable");
        MimePart part = new MimePart(headers, "Hello=20World".getBytes(StandardCharsets.US_ASCII));

        assertThat(part.decodedContentAsString()).isEqualTo("Hello World");
    }

    @Test
    void testAttachmentPart() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Type", "application/pdf; name=\"doc.pdf\"");
        headers.add("Content-Disposition", "attachment; filename=\"doc.pdf\"");
        MimePart part = new MimePart(headers, new byte[0]);

        assertThat(part.isAttachment()).isTrue();
        assertThat(part.filename()).isEqualTo("doc.pdf");
    }

    @Test
    void testFilenameFromContentType() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Type", "application/pdf; name=\"report.pdf\"");
        MimePart part = new MimePart(headers, new byte[0]);

        assertThat(part.filename()).isEqualTo("report.pdf");
    }

    @Test
    void testRawContentIsCopy() {
        byte[] original = {1, 2, 3};
        MimePart part = new MimePart(new MimeHeaders(), original);
        original[0] = 99;
        assertThat(part.rawContent()[0]).isEqualTo((byte) 1);
    }

    @Test
    void testNullContent() {
        MimePart part = new MimePart(new MimeHeaders(), null);
        assertThat(part.rawContent()).isEmpty();
    }

    @Test
    void testContentTransferEncoding() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Transfer-Encoding", "base64");
        MimePart part = new MimePart(headers, new byte[0]);
        assertThat(part.contentTransferEncoding()).isEqualTo(ContentTransferEncoding.BASE64);
    }

    @Test
    void testToString() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Type", "text/plain");
        MimePart part = new MimePart(headers, "hello".getBytes());
        assertThat(part.toString()).contains("text/plain");
    }
}
