package ssg.legoflow.messaging.amqp.session;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.transport.Performative;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link AmqpSession} — session management and flow control.
 */
class AmqpSessionTest {

    @Test void testInitialState() {
        var session = new AmqpSession(0);
        assertThat(session.localChannel()).isEqualTo(0);
        assertThat(session.state()).isEqualTo(AmqpSession.State.UNMAPPED);
        assertThat(session.nextOutgoingId()).isEqualTo(0);
    }

    @Test void testCreateBegin() {
        var session = new AmqpSession(0);
        var begin = session.createBegin();
        assertThat(begin.nextOutgoingId()).isEqualTo(0);
        assertThat(begin.incomingWindow()).isEqualTo(AmqpConstants.DEFAULT_INCOMING_WINDOW);
        assertThat(begin.outgoingWindow()).isEqualTo(AmqpConstants.DEFAULT_OUTGOING_WINDOW);
        assertThat(begin.remoteChannel()).isNull();
    }

    @Test void testHandleBegin() {
        var session = new AmqpSession(0);
        var begin = new Performative.Begin(0, 100, 4096, 4096);
        session.handleBegin(begin);
        assertThat(session.remoteChannel()).isEqualTo(0);
        assertThat(session.nextIncomingId()).isEqualTo(100);
        assertThat(session.remoteIncomingWindow()).isEqualTo(4096);
    }

    @Test void testAllocateDeliveryId() {
        var session = new AmqpSession(0);
        session.handleBegin(new Performative.Begin(0, 0, 100, 100));
        long id1 = session.allocateDeliveryId();
        long id2 = session.allocateDeliveryId();
        assertThat(id1).isEqualTo(0);
        assertThat(id2).isEqualTo(1);
    }

    @Test void testAllocateDeliveryIdExhausted() {
        var session = new AmqpSession(0);
        session.handleBegin(new Performative.Begin(0, 0, 1, 1));
        long id1 = session.allocateDeliveryId();
        assertThat(id1).isEqualTo(0);
        long id2 = session.allocateDeliveryId();
        assertThat(id2).isEqualTo(-1); // Window exhausted
    }

    @Test void testRecordIncomingTransfer() {
        var session = new AmqpSession(0);
        session.handleBegin(new Performative.Begin(0, 0, 100, 100));
        long initialIncoming = session.nextIncomingId();
        session.recordIncomingTransfer();
        assertThat(session.nextIncomingId()).isEqualTo(initialIncoming + 1);
    }

    @Test void testIncomingWindowReplenishment() {
        var session = new AmqpSession(0);
        session.handleBegin(new Performative.Begin(0, 0, 100, 100));
        // Consume most of the window
        long window = session.incomingWindow();
        for (int i = 0; i < window - 1; i++) {
            session.recordIncomingTransfer();
        }
        // At this point the window should have been replenished at least once
        assertThat(session.incomingWindow()).isGreaterThan(0);
    }

    @Test void testHandleFlow() {
        var session = new AmqpSession(0);
        session.handleBegin(new Performative.Begin(0, 0, 100, 100));
        var flow = new Performative.Flow(0L, 200L, 0L, 300L,
                null, null, null, null, false, false, java.util.Map.of());
        session.handleFlow(flow);
        // Remote outgoing window should be updated
        assertThat(session.outgoingWindow()).isGreaterThan(0);
    }

    @Test void testAllocateHandle() {
        var session = new AmqpSession(0);
        assertThat(session.allocateHandle()).isEqualTo(0);
        assertThat(session.allocateHandle()).isEqualTo(1);
        assertThat(session.allocateHandle()).isEqualTo(2);
    }

    @Test void testAddSenderLink() {
        var session = new AmqpSession(0);
        var link = new SenderLink("sender-1", 0, null, "queue");
        session.addSenderLink(link);
        assertThat(session.senderLink(0)).isNotNull();
        assertThat(session.senderLink(0).name()).isEqualTo("sender-1");
    }

    @Test void testAddReceiverLink() {
        var session = new AmqpSession(0);
        var link = new ReceiverLink("receiver-1", 0, "queue", null);
        session.addReceiverLink(link);
        assertThat(session.receiverLink(0)).isNotNull();
        assertThat(session.receiverLink(0).name()).isEqualTo("receiver-1");
    }

    @Test void testHandleForName() {
        var session = new AmqpSession(0);
        var link = new SenderLink("my-link", 5, null, "queue");
        session.addSenderLink(link);
        assertThat(session.handleForName("my-link")).isEqualTo(5);
        assertThat(session.handleForName("unknown")).isNull();
    }

    @Test void testRemoveLink() {
        var session = new AmqpSession(0);
        var link = new SenderLink("sender", 0, null, "queue");
        session.addSenderLink(link);
        assertThat(session.senderLink(0)).isNotNull();
        session.removeLink(0);
        assertThat(session.senderLink(0)).isNull();
        assertThat(session.handleForName("sender")).isNull();
    }

    @Test void testStateTransitions() {
        var session = new AmqpSession(0);
        assertThat(session.state()).isEqualTo(AmqpSession.State.UNMAPPED);
        session.state(AmqpSession.State.BEGIN_SENT);
        assertThat(session.state()).isEqualTo(AmqpSession.State.BEGIN_SENT);
        session.state(AmqpSession.State.MAPPED);
        assertThat(session.state()).isEqualTo(AmqpSession.State.MAPPED);
    }

    @Test void testSetRemoteChannel() {
        var session = new AmqpSession(0);
        assertThat(session.remoteChannel()).isEqualTo(-1);
        session.remoteChannel(5);
        assertThat(session.remoteChannel()).isEqualTo(5);
    }

    @Test void testSetHandleMax() {
        var session = new AmqpSession(0);
        session.handleMax(100);
        assertThat(session.handleMax()).isEqualTo(100);
    }

    @Test void testMultipleLinks() {
        var session = new AmqpSession(0);
        session.addSenderLink(new SenderLink("s1", 0, null, "q1"));
        session.addSenderLink(new SenderLink("s2", 1, null, "q2"));
        session.addReceiverLink(new ReceiverLink("r1", 2, "q1", null));
        assertThat(session.senderLinks()).hasSize(2);
        assertThat(session.receiverLinks()).hasSize(1);
    }
}
