package ssg.legoflow.media.sip.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.header.SipHeaders;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SipRequest}, {@link SipResponse}, and {@link SipMessage}.
 */
class SipMessageTest {

    @Test
    void testCreateRequest() {
        var request = SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=1928301774")
                .to("<sip:bob@biloxi.com>")
                .callId("a84b4c76e66710@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .contact("<sip:alice@192.168.1.1:5060>")
                .build();

        assertThat(request.method()).isEqualTo(SipMethod.INVITE);
        assertThat(request.requestUri()).isEqualTo("sip:bob@biloxi.com");
        assertThat(request.version()).isEqualTo("SIP/2.0");
        assertThat(request.headers().callId()).isEqualTo("a84b4c76e66710@atlanta.com");
    }

    @Test
    void testCreateRequestWithBody() {
        String sdp = "v=0\r\no=alice 2890844526 2890844526 IN IP4 192.168.1.1\r\n";
        var request = SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>")
                .callId("test@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .body(sdp, "application/sdp")
                .build();

        assertThat(request.hasBody()).isTrue();
        assertThat(request.bodyAsString()).isEqualTo(sdp);
        assertThat(request.headers().first(SipHeaders.CONTENT_TYPE)).hasValue("application/sdp");
        assertThat(request.headers().contentLength()).isEqualTo(sdp.length());
    }

    @Test
    void testCreateResponse() {
        var response = SipResponse.builder(SipStatus.OK)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=1928301774")
                .to("<sip:bob@biloxi.com>;tag=a6c85cf")
                .callId("a84b4c76e66710@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .contact("<sip:bob@192.168.1.2:5060>")
                .build();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.reasonPhrase()).isEqualTo("OK");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isFinal()).isTrue();
        assertThat(response.isProvisional()).isFalse();
    }

    @Test
    void testProvisionalResponse() {
        var response = SipResponse.builder(SipStatus.RINGING)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>;tag=def")
                .callId("test@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .build();

        assertThat(response.statusCode()).isEqualTo(180);
        assertThat(response.isProvisional()).isTrue();
        assertThat(response.isFinal()).isFalse();
    }

    @Test
    void testResponseFromRequest() {
        var request = SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=1928301774")
                .to("<sip:bob@biloxi.com>")
                .callId("a84b4c76e66710@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .build();

        var response = SipResponse.builder(SipStatus.OK)
                .fromRequest(request)
                .build();

        assertThat(response.headers().callId()).isEqualTo("a84b4c76e66710@atlanta.com");
        assertThat(response.headers().first(SipHeaders.FROM)).isPresent();
        assertThat(response.headers().first(SipHeaders.TO)).isPresent();
        assertThat(response.headers().first(SipHeaders.CSEQ)).isPresent();
    }

    @Test
    void testSipMessageSealedInterface() {
        SipMessage request = SipRequest.builder(SipMethod.OPTIONS, "sip:server.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:server.com>")
                .callId("test@atlanta.com")
                .cseq(1, SipMethod.OPTIONS)
                .maxForwards(70)
                .build();

        SipMessage response = SipResponse.builder(SipStatus.OK)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:server.com>")
                .callId("test@atlanta.com")
                .cseq(1, SipMethod.OPTIONS)
                .build();

        // Pattern matching with sealed interface
        assertThat(request).isInstanceOf(SipRequest.class);
        assertThat(response).isInstanceOf(SipResponse.class);
    }

    @Test
    void testRequestLine() {
        var request = SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=abc")
                .to("<sip:alice@example.com>")
                .callId("test@example.com")
                .cseq(1, SipMethod.REGISTER)
                .maxForwards(70)
                .build();

        assertThat(request.requestLine()).isEqualTo("REGISTER sip:registrar.example.com SIP/2.0");
    }

    @Test
    void testStatusLine() {
        var response = SipResponse.builder(SipStatus.NOT_FOUND)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>")
                .callId("test@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .build();

        assertThat(response.statusLine()).isEqualTo("SIP/2.0 404 Not Found");
    }

    @Test
    void testResponseWithBody() {
        String sdp = "v=0\r\n";
        var response = SipResponse.builder(SipStatus.OK)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>;tag=def")
                .callId("test@atlanta.com")
                .cseq(1, SipMethod.INVITE)
                .body(sdp, "application/sdp")
                .build();

        assertThat(response.hasBody()).isTrue();
        assertThat(response.bodyAsString()).isEqualTo(sdp);
    }

    @Test
    void testSipMethodFromName() {
        assertThat(SipMethod.fromName("INVITE")).isEqualTo(SipMethod.INVITE);
        assertThat(SipMethod.fromName("invite")).isEqualTo(SipMethod.INVITE);
        assertThat(SipMethod.fromName("BYE")).isEqualTo(SipMethod.BYE);
        assertThat(SipMethod.fromName("REGISTER")).isEqualTo(SipMethod.REGISTER);
        assertThat(SipMethod.fromName("PRACK")).isEqualTo(SipMethod.PRACK);
    }

    @Test
    void testSipMethodFromNameUnknown() {
        assertThatThrownBy(() -> SipMethod.fromName("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSipStatusFromCode() {
        assertThat(SipStatus.fromCode(200)).isEqualTo(SipStatus.OK);
        assertThat(SipStatus.fromCode(180)).isEqualTo(SipStatus.RINGING);
        assertThat(SipStatus.fromCode(486)).isEqualTo(SipStatus.BUSY_HERE);
    }

    @Test
    void testSipStatusProperties() {
        assertThat(SipStatus.TRYING.isProvisional()).isTrue();
        assertThat(SipStatus.OK.isSuccess()).isTrue();
        assertThat(SipStatus.OK.isFinal()).isTrue();
        assertThat(SipStatus.NOT_FOUND.isError()).isTrue();
        assertThat(SipStatus.BUSY_EVERYWHERE.isError()).isTrue();
    }
}
