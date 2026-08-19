package ssg.legoflow.email.common.mime;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link MimeParser}.
 */
class MimeParserTest {

    @Test
    void testParseSimpleTextMessage() {
        String raw = """
                Subject: Hello
                From: sender@example.com
                To: recipient@example.com
                Content-Type: text/plain; charset=utf-8

                Hello, World!""";
        MimeMessage msg = MimeParser.parse(raw);
        assertThat(msg.subject()).isEqualTo("Hello");
        assertThat(msg.isMultipart()).isFalse();
        assertThat(msg.body()).isNotNull();
        String body = new String(msg.body().rawContent(), StandardCharsets.UTF_8);
        assertThat(body).isEqualTo("Hello, World!");
    }

    @Test
    void testParseMultipartMixed() {
        String raw = """
                Subject: Test
                Content-Type: multipart/mixed; boundary="BOUNDARY"

                --BOUNDARY
                Content-Type: text/plain

                Hello, this is the body.
                --BOUNDARY
                Content-Type: application/pdf
                Content-Disposition: attachment; filename="doc.pdf"
                Content-Transfer-Encoding: base64

                SGVsbG8=
                --BOUNDARY--""";
        MimeMessage msg = MimeParser.parse(raw);
        assertThat(msg.isMultipart()).isTrue();
        assertThat(msg.multipartBody().partCount()).isEqualTo(2);

        MimePart textPart = (MimePart) msg.multipartBody().part(0);
        assertThat(textPart.contentType().mediaType()).isEqualTo("text/plain");

        MimePart pdfPart = (MimePart) msg.multipartBody().part(1);
        assertThat(pdfPart.contentType().mediaType()).isEqualTo("application/pdf");
    }

    @Test
    void testParseMultipartAlternative() {
        String raw = """
                Subject: Alternative
                Content-Type: multipart/alternative; boundary="ALT"

                --ALT
                Content-Type: text/plain

                Plain text version
                --ALT
                Content-Type: text/html

                <html><body>HTML version</body></html>
                --ALT--""";
        MimeMessage msg = MimeParser.parse(raw);
        assertThat(msg.isMultipart()).isTrue();
        MimeMultipart mp = msg.multipartBody();
        assertThat(mp.multipartType()).isEqualTo(MultipartType.ALTERNATIVE);
        assertThat(mp.partCount()).isEqualTo(2);
    }

    @Test
    void testParseNestedMultipart() {
        String raw = """
                Content-Type: multipart/mixed; boundary="OUTER"

                --OUTER
                Content-Type: multipart/alternative; boundary="INNER"

                --INNER
                Content-Type: text/plain

                Plain text
                --INNER
                Content-Type: text/html

                <html>HTML</html>
                --INNER--
                --OUTER
                Content-Type: application/octet-stream
                Content-Disposition: attachment; filename="file.bin"

                binary data
                --OUTER--""";
        MimeMessage msg = MimeParser.parse(raw);
        assertThat(msg.isMultipart()).isTrue();
        assertThat(msg.multipartBody().partCount()).isEqualTo(2);

        // First part should be a nested multipart
        Object firstPart = msg.multipartBody().part(0);
        assertThat(firstPart).isInstanceOf(MimeMultipart.class);
        MimeMultipart inner = (MimeMultipart) firstPart;
        assertThat(inner.partCount()).isEqualTo(2);

        // Second part is a simple attachment
        Object secondPart = msg.multipartBody().part(1);
        assertThat(secondPart).isInstanceOf(MimePart.class);
    }

    @Test
    void testParseInlineAttachment() {
        String raw = """
                Content-Type: multipart/related; boundary="REL"

                --REL
                Content-Type: text/html

                <html><img src="cid:image1"/></html>
                --REL
                Content-Type: image/png
                Content-ID: <image1>
                Content-Disposition: inline
                Content-Transfer-Encoding: base64

                iVBORw0KGgo=
                --REL--""";
        MimeMessage msg = MimeParser.parse(raw);
        assertThat(msg.isMultipart()).isTrue();
        assertThat(msg.multipartBody().multipartType()).isEqualTo(MultipartType.RELATED);
        assertThat(msg.multipartBody().partCount()).isEqualTo(2);
    }

    @Test
    void testParseEmptyThrows() {
        assertThatThrownBy(() -> MimeParser.parse((String) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MimeParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseHeadersOnly() {
        String raw = "Subject: No Body\r\nFrom: test@test.com\r\n";
        MimeMessage msg = MimeParser.parse(raw);
        assertThat(msg.subject()).isEqualTo("No Body");
    }

    @Test
    void testAllParts() {
        String raw = """
                Content-Type: multipart/mixed; boundary="B"

                --B
                Content-Type: text/plain

                Text
                --B
                Content-Type: text/html

                <html/>
                --B--""";
        MimeMessage msg = MimeParser.parse(raw);
        assertThat(msg.allParts()).hasSize(2);
    }

    @Test
    void testParseFromBytes() {
        String raw = "Subject: Test\r\n\r\nBody content";
        MimeMessage msg = MimeParser.parse(raw.getBytes(StandardCharsets.UTF_8));
        assertThat(msg.subject()).isEqualTo("Test");
    }

    @Test
    void testParseWithPreamble() {
        String raw = """
                Content-Type: multipart/mixed; boundary="B"

                This is the preamble. It should be ignored.
                --B
                Content-Type: text/plain

                Body
                --B--""";
        MimeMessage msg = MimeParser.parse(raw);
        assertThat(msg.multipartBody().preamble()).isNotNull();
        assertThat(msg.multipartBody().preamble()).contains("preamble");
    }

    @Test
    void testParseMessageAccessors() {
        String raw = """
                From: "John Doe" <john@example.com>
                To: alice@example.com, bob@example.com
                Cc: carol@example.com
                Date: Thu, 13 Feb 2020 15:30:00 +0000
                Message-ID: <msg123@example.com>
                Subject: Test Message
                Content-Type: text/plain

                Body text""";
        MimeMessage msg = MimeParser.parse(raw);
        assertThat(msg.subject()).isEqualTo("Test Message");
        assertThat(msg.from()).hasSize(1);
        assertThat(msg.from().get(0).displayName()).isEqualTo("John Doe");
        assertThat(msg.to()).hasSize(2);
        assertThat(msg.cc()).hasSize(1);
        assertThat(msg.date()).isNotNull();
        assertThat(msg.date().getYear()).isEqualTo(2020);
        assertThat(msg.messageId()).isNotNull();
        assertThat(msg.messageId().id()).isEqualTo("msg123@example.com");
    }
}
