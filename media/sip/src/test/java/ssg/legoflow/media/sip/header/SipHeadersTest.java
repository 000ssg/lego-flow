package ssg.legoflow.media.sip.header;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.protocol.SipMethod;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SipHeaders}.
 */
class SipHeadersTest {

    @Test
    void testSetAndGet() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.CALL_ID, "test@example.com");
        assertThat(headers.first(SipHeaders.CALL_ID)).hasValue("test@example.com");
    }

    @Test
    void testCaseInsensitive() {
        var headers = new SipHeaders();
        headers.set("Call-ID", "test@example.com");
        assertThat(headers.first("call-id")).hasValue("test@example.com");
        assertThat(headers.first("CALL-ID")).hasValue("test@example.com");
    }

    @Test
    void testAddMultipleValues() {
        var headers = new SipHeaders();
        headers.add(SipHeaders.VIA, "SIP/2.0/UDP proxy1.example.com");
        headers.add(SipHeaders.VIA, "SIP/2.0/UDP proxy2.example.com");
        assertThat(headers.all(SipHeaders.VIA)).hasSize(2);
    }

    @Test
    void testSetReplacesExisting() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.CALL_ID, "first");
        headers.set(SipHeaders.CALL_ID, "second");
        assertThat(headers.first(SipHeaders.CALL_ID)).hasValue("second");
        assertThat(headers.all(SipHeaders.CALL_ID)).hasSize(1);
    }

    @Test
    void testContains() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.FROM, "<sip:alice@example.com>");
        assertThat(headers.contains(SipHeaders.FROM)).isTrue();
        assertThat(headers.contains(SipHeaders.TO)).isFalse();
    }

    @Test
    void testRemove() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.EXPIRES, "3600");
        assertThat(headers.contains(SipHeaders.EXPIRES)).isTrue();
        headers.remove(SipHeaders.EXPIRES);
        assertThat(headers.contains(SipHeaders.EXPIRES)).isFalse();
    }

    @Test
    void testCallId() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.CALL_ID, "abc123@host.com");
        assertThat(headers.callId()).isEqualTo("abc123@host.com");
    }

    @Test
    void testCallIdMissingThrows() {
        var headers = new SipHeaders();
        assertThatThrownBy(headers::callId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Call-ID");
    }

    @Test
    void testCSeq() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.CSEQ, "1 INVITE");
        CSeqHeader cseq = headers.cseq();
        assertThat(cseq.sequence()).isEqualTo(1);
        assertThat(cseq.method()).isEqualTo(SipMethod.INVITE);
    }

    @Test
    void testContentLength() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.CONTENT_LENGTH, "142");
        assertThat(headers.contentLength()).isEqualTo(142);
    }

    @Test
    void testContentLengthDefault() {
        var headers = new SipHeaders();
        assertThat(headers.contentLength()).isEqualTo(0);
    }

    @Test
    void testMaxForwards() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.MAX_FORWARDS, "70");
        assertThat(headers.maxForwards()).hasValue(70);
    }

    @Test
    void testExpires() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.EXPIRES, "3600");
        assertThat(headers.expires()).hasValue(3600);
    }

    @Test
    void testCompactFormVia() {
        var headers = new SipHeaders();
        headers.set("v", "SIP/2.0/UDP proxy.example.com");
        assertThat(headers.first(SipHeaders.VIA)).hasValue("SIP/2.0/UDP proxy.example.com");
    }

    @Test
    void testCompactFormFrom() {
        var headers = new SipHeaders();
        headers.set("f", "<sip:alice@example.com>");
        assertThat(headers.first(SipHeaders.FROM)).hasValue("<sip:alice@example.com>");
    }

    @Test
    void testCompactFormTo() {
        var headers = new SipHeaders();
        headers.set("t", "<sip:bob@example.com>");
        assertThat(headers.first(SipHeaders.TO)).hasValue("<sip:bob@example.com>");
    }

    @Test
    void testCompactFormCallId() {
        var headers = new SipHeaders();
        headers.set("i", "abc@host.com");
        assertThat(headers.callId()).isEqualTo("abc@host.com");
    }

    @Test
    void testCompactFormContact() {
        var headers = new SipHeaders();
        headers.set("m", "<sip:alice@192.168.1.1>");
        assertThat(headers.first(SipHeaders.CONTACT)).hasValue("<sip:alice@192.168.1.1>");
    }

    @Test
    void testCompactFormContentLength() {
        var headers = new SipHeaders();
        headers.set("l", "100");
        assertThat(headers.contentLength()).isEqualTo(100);
    }

    @Test
    void testSize() {
        var headers = new SipHeaders();
        assertThat(headers.size()).isEqualTo(0);
        headers.set(SipHeaders.FROM, "alice");
        headers.set(SipHeaders.TO, "bob");
        assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    void testFormat() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.CALL_ID, "test@example.com");
        headers.set(SipHeaders.CSEQ, "1 INVITE");
        String formatted = headers.format();
        assertThat(formatted).contains("Call-Id: test@example.com\r\n");
        assertThat(formatted).contains("Cseq: 1 INVITE\r\n");
    }

    @Test
    void testCopy() {
        var original = new SipHeaders();
        original.set(SipHeaders.CALL_ID, "test@example.com");
        original.add(SipHeaders.VIA, "SIP/2.0/UDP proxy.com");

        var copy = original.copy();
        copy.set(SipHeaders.CALL_ID, "modified@example.com");

        assertThat(original.callId()).isEqualTo("test@example.com");
        assertThat(copy.callId()).isEqualTo("modified@example.com");
    }

    @Test
    void testFromAddress() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.FROM, "<sip:alice@atlanta.com>;tag=1928301774");
        AddressHeader from = headers.from();
        assertThat(from.uri().user()).hasValue("alice");
        assertThat(from.tag()).hasValue("1928301774");
    }

    @Test
    void testToAddress() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.TO, "<sip:bob@biloxi.com>;tag=a6c85cf");
        AddressHeader to = headers.to();
        assertThat(to.uri().user()).hasValue("bob");
        assertThat(to.tag()).hasValue("a6c85cf");
    }

    @Test
    void testTopVia() {
        var headers = new SipHeaders();
        headers.set(SipHeaders.VIA, "SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776");
        ViaHeader via = headers.topVia();
        assertThat(via.host()).isEqualTo("192.168.1.1");
        assertThat(via.port()).isEqualTo(5060);
        assertThat(via.branch()).isEqualTo("z9hG4bK776");
    }
}
