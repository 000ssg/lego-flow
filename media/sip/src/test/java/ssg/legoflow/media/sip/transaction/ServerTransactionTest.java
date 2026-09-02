package ssg.legoflow.media.sip.transaction;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.protocol.SipResponse;
import ssg.legoflow.media.sip.protocol.SipStatus;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ServerTransaction}.
 */
class ServerTransactionTest {

    @Test
    void testInviteServerInitialState() {
        var tx = createInviteServerTx();
        assertThat(tx.state()).isEqualTo(TransactionState.INITIAL);
        assertThat(tx.isInvite()).isTrue();
    }

    @Test
    void testInviteServerStart() {
        var tx = createInviteServerTx();
        tx.start();
        assertThat(tx.state()).isEqualTo(TransactionState.PROCEEDING);
    }

    @Test
    void testInviteServerSendProvisional() {
        var tx = createInviteServerTx();
        tx.start();
        tx.sendResponse(createResponse(180));
        assertThat(tx.state()).isEqualTo(TransactionState.PROCEEDING);
        assertThat(tx.lastResponse()).isNotNull();
    }

    @Test
    void testInviteServerSend2xx() {
        var tx = createInviteServerTx();
        tx.start();
        tx.sendResponse(createResponse(200));
        assertThat(tx.state()).isEqualTo(TransactionState.TERMINATED);
    }

    @Test
    void testInviteServerSend4xx() {
        var tx = createInviteServerTx();
        tx.start();
        tx.sendResponse(createResponse(486));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
    }

    @Test
    void testInviteServerCompletedToConfirmed() {
        var tx = createInviteServerTx();
        tx.start();
        tx.sendResponse(createResponse(486));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
        tx.processAck();
        assertThat(tx.state()).isEqualTo(TransactionState.CONFIRMED);
    }

    @Test
    void testNonInviteServerStart() {
        var tx = createNonInviteServerTx();
        tx.start();
        assertThat(tx.state()).isEqualTo(TransactionState.TRYING);
    }

    @Test
    void testNonInviteServerTryingToProceeding() {
        var tx = createNonInviteServerTx();
        tx.start();
        tx.sendResponse(createResponse(100));
        assertThat(tx.state()).isEqualTo(TransactionState.PROCEEDING);
    }

    @Test
    void testNonInviteServerTryingToCompleted() {
        var tx = createNonInviteServerTx();
        tx.start();
        tx.sendResponse(createResponse(200));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
    }

    @Test
    void testNonInviteServerProceedingToCompleted() {
        var tx = createNonInviteServerTx();
        tx.start();
        tx.sendResponse(createResponse(100));
        assertThat(tx.state()).isEqualTo(TransactionState.PROCEEDING);
        tx.sendResponse(createResponse(200));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
    }

    @Test
    void testOriginalRequest() {
        var tx = createInviteServerTx();
        assertThat(tx.originalRequest().method()).isEqualTo(SipMethod.INVITE);
    }

    @Test
    void testTerminate() {
        var tx = createInviteServerTx();
        tx.start();
        tx.terminate();
        assertThat(tx.state()).isEqualTo(TransactionState.TERMINATED);
    }

    private ServerTransaction createInviteServerTx() {
        var request = SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>")
                .callId("invite@test.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .build();
        return new ServerTransaction("z9hG4bK776", SipMethod.INVITE, request);
    }

    private ServerTransaction createNonInviteServerTx() {
        var request = SipRequest.builder(SipMethod.OPTIONS, "sip:server.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bKopt")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:server.com>")
                .callId("options@test.com")
                .cseq(1, SipMethod.OPTIONS)
                .maxForwards(70)
                .build();
        return new ServerTransaction("z9hG4bKopt", SipMethod.OPTIONS, request);
    }

    private SipResponse createResponse(int statusCode) {
        var status = SipStatus.fromCode(statusCode);
        return SipResponse.builder(status)
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>;tag=def")
                .callId("invite@test.com")
                .cseq(1, SipMethod.INVITE)
                .build();
    }
}
