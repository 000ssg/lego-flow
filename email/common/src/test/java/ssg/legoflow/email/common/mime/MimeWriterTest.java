package ssg.legoflow.email.common.mime;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MimeWriter}.
 */
class MimeWriterTest {

    @Test
    void testWriteSimpleMessage() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Subject", "Hello");
        headers.add("Content-Type", "text/plain");
        MimePart body = new MimePart(new MimeHeaders(), "Hello, World!".getBytes(StandardCharsets.UTF_8));
        MimeMessage msg = new MimeMessage(headers, body);

        byte[] written = MimeWriter.write(msg);
        String result = new String(written, StandardCharsets.UTF_8);
        assertThat(result).contains("Subject: Hello");
        assertThat(result).contains("Hello, World!");
    }

    @Test
    void testRoundTripSimpleMessage() {
        String raw = "Subject: Test\r\nContent-Type: text/plain\r\n\r\nBody text here";
        MimeMessage parsed = MimeParser.parse(raw);
        byte[] written = MimeWriter.write(parsed);
        MimeMessage reparsed = MimeParser.parse(new String(written, StandardCharsets.UTF_8));
        assertThat(reparsed.subject()).isEqualTo("Test");
    }

    @Test
    void testRoundTripMultipartMessage() {
        String raw = """
                Subject: Multipart Test
                Content-Type: multipart/mixed; boundary="BOUNDARY"

                --BOUNDARY
                Content-Type: text/plain

                Hello, this is the body.
                --BOUNDARY
                Content-Type: text/html

                <html><body>HTML</body></html>
                --BOUNDARY--""";
        MimeMessage parsed = MimeParser.parse(raw);
        byte[] written = MimeWriter.write(parsed);
        MimeMessage reparsed = MimeParser.parse(new String(written, StandardCharsets.UTF_8));

        assertThat(reparsed.subject()).isEqualTo("Multipart Test");
        assertThat(reparsed.isMultipart()).isTrue();
        assertThat(reparsed.multipartBody().partCount()).isEqualTo(2);
    }

    @Test
    void testWritePartSerialization() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Type", "text/plain");
        MimePart part = new MimePart(headers, "Part content".getBytes(StandardCharsets.UTF_8));

        byte[] written = MimeWriter.writePart(part);
        String result = new String(written, StandardCharsets.UTF_8);
        assertThat(result).contains("Content-Type: text/plain");
        assertThat(result).contains("Part content");
    }

    @Test
    void testEncodeContentBase64() {
        byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
        byte[] encoded = MimeWriter.encodeContent(data, ContentTransferEncoding.BASE64);
        assertThat(new String(encoded, StandardCharsets.US_ASCII)).isEqualTo("SGVsbG8=");
    }

    @Test
    void testEncodeContentQuotedPrintable() {
        byte[] data = new byte[]{(byte) 0xE9};
        byte[] encoded = MimeWriter.encodeContent(data, ContentTransferEncoding.QUOTED_PRINTABLE);
        assertThat(new String(encoded, StandardCharsets.US_ASCII)).isEqualTo("=E9");
    }

    @Test
    void testEncodeContentSevenBit() {
        byte[] data = "Hello".getBytes(StandardCharsets.US_ASCII);
        byte[] encoded = MimeWriter.encodeContent(data, ContentTransferEncoding.SEVEN_BIT);
        assertThat(encoded).isEqualTo(data);
    }

    @Test
    void testRoundTripPreservesStructure() {
        String raw = """
                Content-Type: multipart/mixed; boundary="OUTER"

                --OUTER
                Content-Type: multipart/alternative; boundary="INNER"

                --INNER
                Content-Type: text/plain

                Text version
                --INNER
                Content-Type: text/html

                HTML version
                --INNER--
                --OUTER
                Content-Type: application/pdf

                PDF data
                --OUTER--""";
        MimeMessage parsed = MimeParser.parse(raw);
        byte[] written = MimeWriter.write(parsed);
        MimeMessage reparsed = MimeParser.parse(new String(written, StandardCharsets.UTF_8));

        assertThat(reparsed.isMultipart()).isTrue();
        assertThat(reparsed.multipartBody().partCount()).isEqualTo(2);
        // First part should be nested multipart
        assertThat(reparsed.allParts()).hasSizeGreaterThanOrEqualTo(3);
    }
}
