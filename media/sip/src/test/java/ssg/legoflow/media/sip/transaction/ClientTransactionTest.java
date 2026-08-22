package ssg.legoflow.media.sip.transaction;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.protocol.SipResponse;
import ssg.legoflow.media.sip.protocol.SipStatus;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ClientTransaction}.
 */
class ClientTransactionTest {

    @Test
    void testInviteClientInitialState() {
        var tx = createInviteClientTx();
        assertThat(tx.state()).isEqualTo(TransactionState.INITIAL);
        assertThat(tx.isInvite()).isTrue();
    }

    @Test
    void testInviteClientStart() {
        var tx = createInviteClientTx();
        tx.start();
        assertThat(tx.state()).isEqualTo(TransactionState.CALLING);
    }

    @Test
    void testInviteClientCallingToProceeding() {
        var tx = createInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(180));
        assertThat(tx.state()).isEqualTo(TransactionState.PROCEEDING);
        assertThat(tx.lastProvisionalResponse()).isNotNull();
        assertThat(tx.lastProvisionalResponse().statusCode()).isEqualTo(180);
    }

    @Test
    void testInviteClientCallingToTerminated2xx() {
        var tx = createInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(200));
        assertThat(tx.state()).isEqualTo(TransactionState.TERMINATED);
        assertThat(tx.finalResponse()).isNotNull();
        assertThat(tx.responseFuture().isDone()).isTrue();
    }

    @Test
    void testInviteClientCallingToCompleted4xx() {
        var tx = createInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(486));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
        assertThat(tx.finalResponse().statusCode()).isEqualTo(486);
    }

    @Test
    void testInviteClientProceedingToTerminated2xx() {
        var tx = createInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(180));
        assertThat(tx.state()).isEqualTo(TransactionState.PROCEEDING);
        tx.processResponse(createResponse(200));
        assertThat(tx.state()).isEqualTo(TransactionState.TERMINATED);
    }

    @Test
    void testInviteClientProceedingToCompleted4xx() {
        var tx = createInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(180));
        tx.processResponse(createResponse(486));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
    }

    @Test
    void testInviteClientMultipleProvisional() {
        var tx = createInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(100));
        assertThat(tx.state()).isEqualTo(TransactionState.PROCEEDING);
        tx.processResponse(createResponse(180));
        assertThat(tx.state()).isEqualTo(TransactionState.PROCEEDING);
        assertThat(tx.lastProvisionalResponse().statusCode()).isEqualTo(180);
    }

    @Test
    void testInviteClientCompletedAbsorbsRetransmission() {
        var tx = createInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(486));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
        // Retransmission should be absorbed
        tx.processResponse(createResponse(486));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
    }

    @Test
    void testNonInviteClientInitialState() {
        var tx = createNonInviteClientTx();
        assertThat(tx.isInvite()).isFalse();
    }

    @Test
    void testNonInviteClientStart() {
        var tx = createNonInviteClientTx();
        tx.start();
        assertThat(tx.state()).isEqualTo(TransactionState.TRYING);
    }

    @Test
    void testNonInviteClientTryingToProceeding() {
        var tx = createNonInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(100));
        assertThat(tx.state()).isEqualTo(TransactionState.PROCEEDING);
    }

    @Test
    void testNonInviteClientTryingToCompleted() {
        var tx = createNonInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(200));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
        assertThat(tx.responseFuture().isDone()).isTrue();
    }

    @Test
    void testNonInviteClientProceedingToCompleted() {
        var tx = createNonInviteClientTx();
        tx.start();
        tx.processResponse(createResponse(100));
        tx.processResponse(createResponse(200));
        assertThat(tx.state()).isEqualTo(TransactionState.COMPLETED);
    }

    @Test
    void testTerminate() {
        var tx = createInviteClientTx();
        tx.start();
        tx.terminate();
        assertThat(tx.state()).isEqualTo(TransactionState.TERMINATED);
        assertThat(tx.isTerminated()).isTrue();
        assertThat(tx.responseFuture().isCancelled()).isTrue();
    }

    @Test
    void testBranchId() {
        var tx = createInviteClientTx();
        assertThat(tx.branchId()).isEqualTo("z9hG4bK776");
    }

    private ClientTransaction createInviteClientTx() {
        var request = createInviteRequest();
        return new ClientTransaction("z9hG4bK776", SipMethod.INVITE, request);
    }

    private ClientTransaction createNonInviteClientTx() {
        var request = SipRequest.builder(SipMethod.OPTIONS, "sip:server.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bKopt")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:server.com>")
                .callId("options@test.com")
                .cseq(1, SipMethod.OPTIONS)
                .maxForwards(70)
                .build();
        return new ClientTransaction("z9hG4bKopt", SipMethod.OPTIONS, request);
    }

    private SipRequest createInviteRequest() {
        return SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@atlanta.com>;tag=abc")
                .to("<sip:bob@biloxi.com>")
                .callId("invite@test.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .build();
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
