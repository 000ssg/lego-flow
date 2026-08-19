package ssg.legoflow.media.sip.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.media.common.codec.SdpParser;
import ssg.legoflow.media.common.sdp.SessionDescription;
import ssg.legoflow.media.sip.dialog.DialogState;
import ssg.legoflow.media.sip.header.SipHeaders;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.registration.SipRegistrar;
import ssg.legoflow.media.sip.transaction.ClientTransaction;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SipUserAgent}.
 */
class SipUserAgentTest {

    private static final String SDP_OFFER =
            "v=0\r\n"
            + "o=alice 2890844526 2890844526 IN IP4 192.168.1.1\r\n"
            + "s=Session\r\n"
            + "c=IN IP4 192.168.1.1\r\n"
            + "t=0 0\r\n"
            + "m=audio 49170 RTP/AVP 0 8\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n"
            + "a=rtpmap:8 PCMA/8000\r\n";

    private static final String SDP_ANSWER =
            "v=0\r\n"
            + "o=bob 2890844527 2890844527 IN IP4 192.168.1.2\r\n"
            + "s=Session\r\n"
            + "c=IN IP4 192.168.1.2\r\n"
            + "t=0 0\r\n"
            + "m=audio 49172 RTP/AVP 0 8\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n"
            + "a=rtpmap:8 PCMA/8000\r\n";

    private SipUserAgent alice;
    private SipUserAgent bob;

    @BeforeEach
    void setUp() {
        alice = new SipUserAgent("sip:alice@atlanta.com", "sip:alice@192.168.1.1:5060");
        bob = new SipUserAgent("sip:bob@biloxi.com", "sip:bob@192.168.1.2:5060");
    }

    @AfterEach
    void tearDown() {
        alice.close();
        bob.close();
    }

    @Test
    void testCreateInvite() {
        var invite = alice.createInvite("sip:bob@biloxi.com");
        assertThat(invite.method()).isEqualTo(SipMethod.INVITE);
        assertThat(invite.requestUri()).isEqualTo("sip:bob@biloxi.com");
        assertThat(invite.headers().first(SipHeaders.FROM).orElse("")).contains("sip:alice@atlanta.com");
        assertThat(invite.headers().first(SipHeaders.TO).orElse("")).contains("sip:bob@biloxi.com");
        assertThat(invite.headers().first(SipHeaders.CONTACT).orElse("")).contains("sip:alice@192.168.1.1:5060");
        assertThat(invite.headers().callId()).isNotEmpty();
    }

    @Test
    void testCreateInviteWithSdp() {
        SessionDescription sdp = SdpParser.parse(SDP_OFFER);
        alice.setLocalSdp(sdp);

        var invite = alice.createInvite("sip:bob@biloxi.com");
        assertThat(invite.hasBody()).isTrue();
        assertThat(invite.headers().first(SipHeaders.CONTENT_TYPE)).hasValue("application/sdp");
    }

    @Test
    void testHandleOptions() {
        var options = SipRequest.builder(SipMethod.OPTIONS, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>")
                .callId("options@test.com")
                .cseq(1, SipMethod.OPTIONS)
                .maxForwards(70)
                .build();

        var response = bob.handleRequest(options);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().first(SipHeaders.ALLOW).orElse("")).contains("INVITE");
    }

    @Test
    void testHandleRegisterWithRegistrar() {
        var registrar = new SipRegistrar("example.com");
        var ua = new SipUserAgent("sip:registrar@example.com", "sip:registrar@192.168.1.1:5060", registrar);

        var register = SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=abc")
                .to("<sip:alice@example.com>")
                .callId("register@test.com")
                .cseq(1, SipMethod.REGISTER)
                .maxForwards(70)
                .contact("<sip:alice@192.168.1.1:5060>")
                .expires(3600)
                .build();

        var response = ua.handleRequest(register);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(registrar.bindingCount()).isEqualTo(1);

        ua.close();
    }

    @Test
    void testHandleRegisterWithoutRegistrar() {
        var register = SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=abc")
                .to("<sip:alice@example.com>")
                .callId("register@test.com")
                .cseq(1, SipMethod.REGISTER)
                .maxForwards(70)
                .build();

        var response = bob.handleRequest(register);
        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void testCallSetupAndTeardown() {
        // Set SDP capabilities on both sides
        SessionDescription aliceSdp = SdpParser.parse(SDP_OFFER);
        SessionDescription bobSdp = SdpParser.parse(SDP_ANSWER);
        alice.setLocalSdp(aliceSdp);
        bob.setLocalSdp(bobSdp);

        // Alice creates INVITE
        var invite = alice.createInvite("sip:bob@biloxi.com");
        String branch = invite.headers().topVia().branch();

        // Register client transaction for Alice
        var clientTx = new ClientTransaction(branch, SipMethod.INVITE, invite);
        clientTx.start();
        alice.addClientTransaction(branch, clientTx);

        // Bob handles INVITE -> returns 200 OK
        var okResponse = bob.handleRequest(invite);
        assertThat(okResponse).isNotNull();
        assertThat(okResponse.statusCode()).isEqualTo(200);

        // Bob should have a dialog
        assertThat(bob.dialogs()).isNotEmpty();
        var bobDialog = bob.dialogs().values().iterator().next();
        assertThat(bobDialog.state()).isEqualTo(DialogState.CONFIRMED);

        // Alice processes the response
        alice.processResponse(okResponse);
        assertThat(alice.dialogs()).isNotEmpty();

        // Verify SDP negotiation
        assertThat(alice.remoteSdp()).isNotNull();
        assertThat(bob.remoteSdp()).isNotNull();

        // Alice sends ACK
        var ack = alice.createAck(invite, okResponse);
        assertThat(ack.method()).isEqualTo(SipMethod.ACK);
        bob.handleRequest(ack); // ACK returns null

        // Alice sends BYE
        var aliceDialog = alice.dialogs().values().iterator().next();
        var bye = alice.createBye(aliceDialog);
        assertThat(bye.method()).isEqualTo(SipMethod.BYE);

        var byeResponse = bob.handleRequest(bye);
        assertThat(byeResponse.statusCode()).isEqualTo(200);
    }

    @Test
    void testHandleCancel() {
        // Bob sets up with SDP
        SessionDescription bobSdp = SdpParser.parse(SDP_ANSWER);
        bob.setLocalSdp(bobSdp);

        // Create INVITE
        var invite = SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bKcancel")
                .from("<sip:alice@atlanta.com>;tag=cancelTag")
                .to("<sip:bob@biloxi.com>")
                .callId("cancel@test.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .build();

        bob.handleRequest(invite);

        // Send CANCEL
        var cancel = SipRequest.builder(SipMethod.CANCEL, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bKcancel")
                .from("<sip:alice@atlanta.com>;tag=cancelTag")
                .to("<sip:bob@biloxi.com>")
                .callId("cancel@test.com")
                .cseq(1, SipMethod.CANCEL)
                .maxForwards(70)
                .build();

        var cancelResponse = bob.handleRequest(cancel);
        assertThat(cancelResponse.statusCode()).isEqualTo(200);
    }

    @Test
    void testHandleUnsupportedMethod() {
        var subscribe = SipRequest.builder(SipMethod.SUBSCRIBE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>")
                .callId("subscribe@test.com")
                .cseq(1, SipMethod.SUBSCRIBE)
                .maxForwards(70)
                .build();

        var response = bob.handleRequest(subscribe);
        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void testUserAgentProperties() {
        assertThat(alice.aor()).isEqualTo("sip:alice@atlanta.com");
        assertThat(alice.contactUri()).isEqualTo("sip:alice@192.168.1.1:5060");
    }

    @Test
    void testCloseTerminatesDialogs() {
        SessionDescription aliceSdp = SdpParser.parse(SDP_OFFER);
        SessionDescription bobSdp = SdpParser.parse(SDP_ANSWER);
        alice.setLocalSdp(aliceSdp);
        bob.setLocalSdp(bobSdp);

        var invite = alice.createInvite("sip:bob@biloxi.com");
        String branch = invite.headers().topVia().branch();
        var clientTx = new ClientTransaction(branch, SipMethod.INVITE, invite);
        clientTx.start();
        alice.addClientTransaction(branch, clientTx);

        var okResponse = bob.handleRequest(invite);
        alice.processResponse(okResponse);

        assertThat(alice.dialogs()).isNotEmpty();
        alice.close();
        // After close, dialogs should be cleared
        assertThat(alice.dialogs()).isEmpty();
    }
}
