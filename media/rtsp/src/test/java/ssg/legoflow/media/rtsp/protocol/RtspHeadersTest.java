package ssg.legoflow.media.rtsp.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RtspHeaders}.
 */
class RtspHeadersTest {

    @Test
    void testSetAndGet() {
        var headers = new RtspHeaders();
        headers.set("Content-Type", "application/sdp");
        assertThat(headers.first("Content-Type")).hasValue("application/sdp");
    }

    @Test
    void testCaseInsensitiveLookup() {
        var headers = new RtspHeaders();
        headers.set("Content-Type", "application/sdp");
        assertThat(headers.first("content-type")).hasValue("application/sdp");
        assertThat(headers.first("CONTENT-TYPE")).hasValue("application/sdp");
    }

    @Test
    void testAddMultipleValues() {
        var headers = new RtspHeaders();
        headers.add("Accept", "application/sdp");
        headers.add("Accept", "text/plain");
        assertThat(headers.all("Accept")).containsExactly("application/sdp", "text/plain");
    }

    @Test
    void testSetReplacesValue() {
        var headers = new RtspHeaders();
        headers.set("CSeq", "1");
        headers.set("CSeq", "2");
        assertThat(headers.first("CSeq")).hasValue("2");
        assertThat(headers.all("CSeq")).hasSize(1);
    }

    @Test
    void testContains() {
        var headers = new RtspHeaders();
        headers.set("CSeq", "1");
        assertThat(headers.contains("CSeq")).isTrue();
        assertThat(headers.contains("cseq")).isTrue();
        assertThat(headers.contains("Missing")).isFalse();
    }

    @Test
    void testCseq() {
        var headers = new RtspHeaders();
        headers.set("CSeq", "42");
        assertThat(headers.cseq()).isEqualTo(42);
    }

    @Test
    void testCseqMissingThrows() {
        var headers = new RtspHeaders();
        assertThatThrownBy(headers::cseq)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testSessionId() {
        var headers = new RtspHeaders();
        headers.set("Session", "abc123;timeout=60");
        assertThat(headers.sessionId()).hasValue("abc123");
    }

    @Test
    void testSessionIdWithoutTimeout() {
        var headers = new RtspHeaders();
        headers.set("Session", "abc123");
        assertThat(headers.sessionId()).hasValue("abc123");
    }

    @Test
    void testSessionTimeout() {
        var headers = new RtspHeaders();
        headers.set("Session", "abc123;timeout=30");
        assertThat(headers.sessionTimeout()).hasValue(30);
    }

    @Test
    void testSessionTimeoutMissing() {
        var headers = new RtspHeaders();
        headers.set("Session", "abc123");
        assertThat(headers.sessionTimeout()).isEmpty();
    }

    @Test
    void testContentLength() {
        var headers = new RtspHeaders();
        headers.set("Content-Length", "256");
        assertThat(headers.contentLength()).isEqualTo(256);
    }

    @Test
    void testContentLengthMissing() {
        var headers = new RtspHeaders();
        assertThat(headers.contentLength()).isEqualTo(0);
    }

    @Test
    void testSize() {
        var headers = new RtspHeaders();
        assertThat(headers.size()).isEqualTo(0);
        headers.set("CSeq", "1");
        headers.set("Session", "abc");
        assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    void testFormat() {
        var headers = new RtspHeaders();
        headers.set("CSeq", "1");
        headers.set("Content-Type", "application/sdp");
        String formatted = headers.format();
        assertThat(formatted).contains("Cseq: 1\r\n");
        assertThat(formatted).contains("Content-Type: application/sdp\r\n");
    }

    @Test
    void testFirstMissing() {
        var headers = new RtspHeaders();
        assertThat(headers.first("Missing")).isEmpty();
    }

    @Test
    void testAllMissing() {
        var headers = new RtspHeaders();
        assertThat(headers.all("Missing")).isEmpty();
    }

    @Test
    void testToMap() {
        var headers = new RtspHeaders();
        headers.set("CSeq", "1");
        var map = headers.toMap();
        assertThat(map).containsKey("cseq");
    }
}
