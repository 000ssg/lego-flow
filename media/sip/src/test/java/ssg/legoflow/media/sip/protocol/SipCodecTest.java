package ssg.legoflow.media.sip.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.header.SipHeaders;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SipCodec}.
 */
class SipCodecTest {

    @Test
    void testEncodeRequest() {
        var request = SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=1928301774")
                .to("<sip:bob@biloxi.com>")
                .callId("a84b4c76e66710@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .build();

        byte[] encoded = SipCodec.encode(request);
        String text = new String(encoded);
        assertThat(text).startsWith("INVITE sip:bob@biloxi.com SIP/2.0\r\n");
        assertThat(text).contains("Call-Id: a84b4c76e66710@atlanta.com\r\n");
        assertThat(text).endsWith("\r\n\r\n");
    }

    @Test
    void testEncodeRequestWithBody() {
        String body = "v=0\r\n";
        var request = SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>")
                .callId("test@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .body(body, "application/sdp")
                .build();

        byte[] encoded = SipCodec.encode(request);
        String text = new String(encoded);
        assertThat(text).contains("Content-Type: application/sdp\r\n");
        assertThat(text).contains("Content-Length: " + body.length() + "\r\n");
        assertThat(text).endsWith(body);
    }

    @Test
    void testEncodeResponse() {
        var response = SipResponse.builder(SipStatus.OK)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>;tag=def")
                .callId("test@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .build();

        byte[] encoded = SipCodec.encode(response);
        String text = new String(encoded);
        assertThat(text).startsWith("SIP/2.0 200 OK\r\n");
        assertThat(text).endsWith("\r\n\r\n");
    }

    @Test
    void testDecodeRequest() throws IOException {
        String raw = "INVITE sip:bob@biloxi.com SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776\r\n"
                + "From: <sip:alice@atlanta.com>;tag=1928301774\r\n"
                + "To: <sip:bob@biloxi.com>\r\n"
                + "Call-ID: a84b4c76e66710@atlanta.com\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Max-Forwards: 70\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n";

        var request = SipCodec.decodeRequest(raw.getBytes());
        assertThat(request.method()).isEqualTo(SipMethod.INVITE);
        assertThat(request.requestUri()).isEqualTo("sip:bob@biloxi.com");
        assertThat(request.version()).isEqualTo("SIP/2.0");
        assertThat(request.headers().callId()).isEqualTo("a84b4c76e66710@atlanta.com");
        assertThat(request.headers().cseq().sequence()).isEqualTo(1);
        assertThat(request.headers().cseq().method()).isEqualTo(SipMethod.INVITE);
    }

    @Test
    void testDecodeRequestWithBody() throws IOException {
        String body = "v=0\r\no=alice 1 1 IN IP4 192.168.1.1\r\n";
        String raw = "INVITE sip:bob@biloxi.com SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776\r\n"
                + "From: <sip:alice@atlanta.com>;tag=abc\r\n"
                + "To: <sip:bob@biloxi.com>\r\n"
                + "Call-ID: test@atlanta.com\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Content-Type: application/sdp\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "\r\n"
                + body;

        var request = SipCodec.decodeRequest(raw.getBytes());
        assertThat(request.hasBody()).isTrue();
        assertThat(request.bodyAsString()).isEqualTo(body);
    }

    @Test
    void testDecodeResponse() throws IOException {
        String raw = "SIP/2.0 200 OK\r\n"
                + "Via: SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776\r\n"
                + "From: <sip:alice@atlanta.com>;tag=abc\r\n"
                + "To: <sip:bob@biloxi.com>;tag=def\r\n"
                + "Call-ID: test@atlanta.com\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n";

        var response = SipCodec.decodeResponse(raw.getBytes());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.reasonPhrase()).isEqualTo("OK");
        assertThat(response.headers().callId()).isEqualTo("test@atlanta.com");
    }

    @Test
    void testDecodeResponseWithBody() throws IOException {
        String body = "v=0\r\n";
        String raw = "SIP/2.0 200 OK\r\n"
                + "Via: SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776\r\n"
                + "From: <sip:alice@atlanta.com>;tag=abc\r\n"
                + "To: <sip:bob@biloxi.com>;tag=def\r\n"
                + "Call-ID: test@atlanta.com\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Content-Type: application/sdp\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "\r\n"
                + body;

        var response = SipCodec.decodeResponse(raw.getBytes());
        assertThat(response.hasBody()).isTrue();
        assertThat(response.bodyAsString()).isEqualTo(body);
    }

    @Test
    void testAutoDetectRequest() throws IOException {
        String raw = "OPTIONS sip:server.com SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776\r\n"
                + "From: <sip:alice@atlanta.com>;tag=abc\r\n"
                + "To: <sip:server.com>\r\n"
                + "Call-ID: test@atlanta.com\r\n"
                + "CSeq: 1 OPTIONS\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n";

        SipMessage message = SipCodec.decode(raw.getBytes());
        assertThat(message).isInstanceOf(SipRequest.class);
        assertThat(((SipRequest) message).method()).isEqualTo(SipMethod.OPTIONS);
    }

    @Test
    void testAutoDetectResponse() throws IOException {
        String raw = "SIP/2.0 180 Ringing\r\n"
                + "Via: SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776\r\n"
                + "From: <sip:alice@atlanta.com>;tag=abc\r\n"
                + "To: <sip:bob@biloxi.com>;tag=def\r\n"
                + "Call-ID: test@atlanta.com\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n";

        SipMessage message = SipCodec.decode(raw.getBytes());
        assertThat(message).isInstanceOf(SipResponse.class);
        assertThat(((SipResponse) message).statusCode()).isEqualTo(180);
    }

    @Test
    void testRoundTripRequest() throws IOException {
        var original = SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=abc")
                .to("<sip:alice@example.com>")
                .callId("roundtrip@example.com")
                .cseq(1, SipMethod.REGISTER)
                .maxForwards(70)
                .contact("<sip:alice@192.168.1.1:5060>")
                .expires(3600)
                .build();

        byte[] encoded = SipCodec.encode(original);
        var decoded = SipCodec.decodeRequest(encoded);

        assertThat(decoded.method()).isEqualTo(SipMethod.REGISTER);
        assertThat(decoded.requestUri()).isEqualTo("sip:registrar.example.com");
        assertThat(decoded.headers().callId()).isEqualTo("roundtrip@example.com");
    }

    @Test
    void testRoundTripResponse() throws IOException {
        var original = SipResponse.builder(SipStatus.OK)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>;tag=def")
                .callId("roundtrip@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .contact("<sip:bob@192.168.1.2:5060>")
                .build();

        byte[] encoded = SipCodec.encode(original);
        var decoded = SipCodec.decodeResponse(encoded);

        assertThat(decoded.statusCode()).isEqualTo(200);
        assertThat(decoded.headers().callId()).isEqualTo("roundtrip@atlanta.com");
    }

    @Test
    void testDecodeRequestEmptyThrows() {
        assertThatThrownBy(() -> SipCodec.decodeRequest("\r\n".getBytes()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testDecodeResponseEmptyThrows() {
        assertThatThrownBy(() -> SipCodec.decodeResponse("\r\n".getBytes()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testHeaderFolding() throws IOException {
        String raw = "INVITE sip:bob@biloxi.com SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 192.168.1.1:5060\r\n"
                + "  ;branch=z9hG4bK776\r\n"
                + "From: <sip:alice@atlanta.com>;tag=abc\r\n"
                + "To: <sip:bob@biloxi.com>\r\n"
                + "Call-ID: fold@test.com\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n";

        var request = SipCodec.decodeRequest(raw.getBytes());
        String via = request.headers().first(SipHeaders.VIA).orElse("");
        assertThat(via).contains("branch=z9hG4bK776");
    }

    @Test
    void testCompactFormHeaders() throws IOException {
        String raw = "INVITE sip:bob@biloxi.com SIP/2.0\r\n"
                + "v: SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776\r\n"
                + "f: <sip:alice@atlanta.com>;tag=abc\r\n"
                + "t: <sip:bob@biloxi.com>\r\n"
                + "i: compact@test.com\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "l: 0\r\n"
                + "\r\n";

        var request = SipCodec.decodeRequest(raw.getBytes());
        // Compact forms should be resolved to full names
        assertThat(request.headers().first(SipHeaders.VIA)).isPresent();
        assertThat(request.headers().first(SipHeaders.FROM)).isPresent();
        assertThat(request.headers().first(SipHeaders.TO)).isPresent();
        assertThat(request.headers().callId()).isEqualTo("compact@test.com");
    }
}
