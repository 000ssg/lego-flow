package ssg.legoflow.email.common.mime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MimeHeaders}.
 */
class MimeHeadersTest {

    @Test
    void testAddAndGet() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Subject", "Hello");
        assertThat(headers.get("Subject")).isEqualTo("Hello");
    }

    @Test
    void testCaseInsensitiveLookup() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Type", "text/plain");
        assertThat(headers.get("content-type")).isEqualTo("text/plain");
        assertThat(headers.get("CONTENT-TYPE")).isEqualTo("text/plain");
    }

    @Test
    void testSetReplacesExisting() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Subject", "Old");
        headers.set("Subject", "New");
        assertThat(headers.get("Subject")).isEqualTo("New");
        assertThat(headers.getAll("Subject")).hasSize(1);
    }

    @Test
    void testMultipleHeaders() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Received", "from server1");
        headers.add("Received", "from server2");
        assertThat(headers.getAll("Received")).hasSize(2);
        assertThat(headers.get("Received")).isEqualTo("from server1"); // First match
    }

    @Test
    void testContains() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Subject", "test");
        assertThat(headers.contains("Subject")).isTrue();
        assertThat(headers.contains("subject")).isTrue();
        assertThat(headers.contains("From")).isFalse();
    }

    @Test
    void testRemove() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Subject", "test");
        headers.remove("Subject");
        assertThat(headers.contains("Subject")).isFalse();
    }

    @Test
    void testGetNonExistent() {
        MimeHeaders headers = new MimeHeaders();
        assertThat(headers.get("Nonexistent")).isNull();
    }

    @Test
    void testFoldLongLine() {
        // Use words separated by spaces so the fold algorithm can break
        var sb = new StringBuilder("Subject:");
        for (int i = 0; i < 30; i++) {
            sb.append(" word").append(i);
        }
        String longHeader = sb.toString();
        String folded = MimeHeaders.fold(longHeader);
        assertThat(folded).contains("\r\n ");
        for (String line : folded.split("\r\n")) {
            assertThat(line.length()).isLessThanOrEqualTo(MimeHeaders.MAX_LINE_LENGTH + 10);
        }
    }

    @Test
    void testFoldShortLine() {
        String shortHeader = "Subject: Hello";
        assertThat(MimeHeaders.fold(shortHeader)).isEqualTo(shortHeader);
    }

    @Test
    void testUnfold() {
        assertThat(MimeHeaders.unfold("Hello\r\n World")).isEqualTo("Hello World");
        assertThat(MimeHeaders.unfold("Hello\n\tWorld")).isEqualTo("Hello World");
        assertThat(MimeHeaders.unfold("Hello World")).isEqualTo("Hello World");
        assertThat(MimeHeaders.unfold(null)).isNull();
    }

    @Test
    void testParse() {
        String block = "Subject: Hello\r\nFrom: user@example.com\r\nContent-Type: text/plain\r\n";
        MimeHeaders headers = MimeHeaders.parse(block);
        assertThat(headers.size()).isEqualTo(3);
        assertThat(headers.get("Subject")).isEqualTo("Hello");
        assertThat(headers.get("From")).isEqualTo("user@example.com");
    }

    @Test
    void testParseFoldedHeader() {
        String block = "Subject: This is a very long\r\n subject line\r\n";
        MimeHeaders headers = MimeHeaders.parse(block);
        assertThat(headers.get("Subject")).isEqualTo("This is a very long subject line");
    }

    @Test
    void testParseWithLF() {
        String block = "Subject: Hello\nFrom: user@example.com\n";
        MimeHeaders headers = MimeHeaders.parse(block);
        assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    void testParseEmpty() {
        MimeHeaders headers = MimeHeaders.parse("");
        assertThat(headers.isEmpty()).isTrue();
    }

    @Test
    void testToWireFormat() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Subject", "Hello");
        headers.add("From", "user@example.com");
        String wire = headers.toWireFormat();
        assertThat(wire).contains("Subject: Hello\r\n");
        assertThat(wire).contains("From: user@example.com\r\n");
    }

    @Test
    void testContentType() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Type", "text/html; charset=utf-8");
        ContentType ct = headers.contentType();
        assertThat(ct.mediaType()).isEqualTo("text/html");
    }

    @Test
    void testContentTypeDefault() {
        MimeHeaders headers = new MimeHeaders();
        ContentType ct = headers.contentType();
        assertThat(ct.mediaType()).isEqualTo("text/plain");
    }

    @Test
    void testContentTransferEncoding() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Transfer-Encoding", "base64");
        assertThat(headers.contentTransferEncoding()).isEqualTo(ContentTransferEncoding.BASE64);
    }

    @Test
    void testContentDisposition() {
        MimeHeaders headers = new MimeHeaders();
        headers.add("Content-Disposition", "attachment; filename=\"test.pdf\"");
        ContentDisposition cd = headers.contentDisposition();
        assertThat(cd).isNotNull();
        assertThat(cd.filename()).isEqualTo("test.pdf");
    }

    @Test
    void testSizeAndIsEmpty() {
        MimeHeaders headers = new MimeHeaders();
        assertThat(headers.isEmpty()).isTrue();
        assertThat(headers.size()).isEqualTo(0);
        headers.add("Subject", "test");
        assertThat(headers.isEmpty()).isFalse();
        assertThat(headers.size()).isEqualTo(1);
    }
}
