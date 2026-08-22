package ssg.legoflow.media.sip.dialog;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.header.SipHeaders;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.protocol.SipResponse;
import ssg.legoflow.media.sip.protocol.SipStatus;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SipDialog}.
 */
class SipDialogTest {

    @Test
    void testCreateUacDialogFromInvite() {
        var invite = createInviteRequest();
        var response = createOkResponse();

        var dialog = SipDialog.createFromUac(invite, response);

        assertThat(dialog.callId()).isEqualTo("dialog@test.com");
        assertThat(dialog.localTag()).isEqualTo("aliceTag");
        assertThat(dialog.remoteTag()).isEqualTo("bobTag");
        assertThat(dialog.isUac()).isTrue();
        assertThat(dialog.state()).isEqualTo(DialogState.CONFIRMED);
    }

    @Test
    void testCreateUacDialogEarlyState() {
        var invite = createInviteRequest();
        var ringing = createRingingResponse();

        var dialog = SipDialog.createFromUac(invite, ringing);

        assertThat(dialog.state()).isEqualTo(DialogState.EARLY);
        assertThat(dialog.remoteTag()).isEqualTo("bobTag");
    }

    @Test
    void testCreateUasDialog() {
        var invite = createInviteRequest();
        var dialog = SipDialog.createFromUas(invite, "serverTag");

        assertThat(dialog.callId()).isEqualTo("dialog@test.com");
        assertThat(dialog.localTag()).isEqualTo("serverTag");
        assertThat(dialog.remoteTag()).isEqualTo("aliceTag");
        assertThat(dialog.isUac()).isFalse();
        assertThat(dialog.state()).isEqualTo(DialogState.EARLY);
    }

    @Test
    void testConfirmDialog() {
        var invite = createInviteRequest();
        var dialog = SipDialog.createFromUas(invite, "serverTag");

        assertThat(dialog.state()).isEqualTo(DialogState.EARLY);
        dialog.confirm();
        assertThat(dialog.state()).isEqualTo(DialogState.CONFIRMED);
    }

    @Test
    void testTerminateDialog() {
        var invite = createInviteRequest();
        var response = createOkResponse();
        var dialog = SipDialog.createFromUac(invite, response);

        dialog.terminate();
        assertThat(dialog.state()).isEqualTo(DialogState.TERMINATED);
        assertThat(dialog.state().isActive()).isFalse();
    }

    @Test
    void testDialogId() {
        var invite = createInviteRequest();
        var response = createOkResponse();
        var dialog = SipDialog.createFromUac(invite, response);

        assertThat(dialog.dialogId()).isEqualTo("dialog@test.com:aliceTag:bobTag");
    }

    @Test
    void testLocalCSeqIncrement() {
        var invite = createInviteRequest();
        var response = createOkResponse();
        var dialog = SipDialog.createFromUac(invite, response);

        long first = dialog.nextLocalCSeq();
        long second = dialog.nextLocalCSeq();
        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    void testValidateRemoteCSeq() {
        var invite = createInviteRequest();
        var dialog = SipDialog.createFromUas(invite, "serverTag");

        // CSeq from INVITE is 1, so 2 should be valid
        assertThat(dialog.validateRemoteCSeq(2)).isTrue();
        // Retransmission of same CSeq should be valid
        assertThat(dialog.validateRemoteCSeq(2)).isTrue();
        // Lower CSeq should be invalid
        assertThat(dialog.validateRemoteCSeq(1)).isFalse();
    }

    @Test
    void testCreateInDialogRequest() {
        var invite = createInviteRequest();
        var response = createOkResponse();
        var dialog = SipDialog.createFromUac(invite, response);

        var bye = dialog.createRequest(SipMethod.BYE);
        var built = bye.via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bKbye").build();

        assertThat(built.method()).isEqualTo(SipMethod.BYE);
        assertThat(built.headers().callId()).isEqualTo("dialog@test.com");
        assertThat(built.headers().first(SipHeaders.FROM).orElse("")).contains("aliceTag");
    }

    @Test
    void testDialogEquality() {
        var invite = createInviteRequest();
        var response = createOkResponse();
        var dialog1 = SipDialog.createFromUac(invite, response);
        var dialog2 = SipDialog.createFromUac(invite, response);

        assertThat(dialog1).isEqualTo(dialog2);
        assertThat(dialog1.hashCode()).isEqualTo(dialog2.hashCode());
    }

    @Test
    void testDialogStateActive() {
        assertThat(DialogState.EARLY.isActive()).isTrue();
        assertThat(DialogState.CONFIRMED.isActive()).isTrue();
        assertThat(DialogState.TERMINATED.isActive()).isFalse();
    }

    @Test
    void testRemoteTarget() {
        var invite = createInviteRequest();
        var response = createOkResponse();
        var dialog = SipDialog.createFromUac(invite, response);

        assertThat(dialog.remoteTarget()).isEqualTo("sip:bob@192.168.1.2:5060");
    }

    private SipRequest createInviteRequest() {
        return SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=aliceTag")
                .to("<sip:bob@biloxi.com>")
                .callId("dialog@test.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .contact("<sip:alice@192.168.1.1:5060>")
                .build();
    }

    private SipResponse createOkResponse() {
        return SipResponse.builder(SipStatus.OK)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=aliceTag")
                .to("<sip:bob@biloxi.com>;tag=bobTag")
                .callId("dialog@test.com")
                .cseq(1, SipMethod.INVITE)
                .contact("<sip:bob@192.168.1.2:5060>")
                .build();
    }

    private SipResponse createRingingResponse() {
        return SipResponse.builder(SipStatus.RINGING)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=aliceTag")
                .to("<sip:bob@biloxi.com>;tag=bobTag")
                .callId("dialog@test.com")
                .cseq(1, SipMethod.INVITE)
                .build();
    }
}
