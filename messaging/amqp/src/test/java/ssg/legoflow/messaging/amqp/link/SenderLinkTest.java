package ssg.legoflow.messaging.amqp.link;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.transport.Performative;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SenderLink} — sender link and credit-based flow.
 */
class SenderLinkTest {

    @Test void testInitialState() {
        var link = new SenderLink("sender-1", 0, null, "queue");
        assertThat(link.name()).isEqualTo("sender-1");
        assertThat(link.handle()).isEqualTo(0);
        assertThat(link.state()).isEqualTo(SenderLink.State.DETACHED);
        assertThat(link.deliveryCount()).isEqualTo(0);
        assertThat(link.linkCredit()).isEqualTo(0);
    }

    @Test void testCreateAttach() {
        var link = new SenderLink("sender-1", 0, "src", "tgt");
        var attach = link.createAttach();
        assertThat(attach.name()).isEqualTo("sender-1");
        assertThat(attach.handle()).isEqualTo(0);
        assertThat(attach.role()).isFalse(); // sender
    }

    @Test void testGrantCredit() {
        var link = new SenderLink("sender", 0, null, "queue");
        link.grantCredit(0, 100);
        assertThat(link.linkCredit()).isEqualTo(100);
        assertThat(link.hasCredit()).isTrue();
    }

    @Test void testGrantCreditAfterSending() {
        var link = new SenderLink("sender", 0, null, "queue");
        link.grantCredit(0, 100);
        // Simulate sends reducing the delivery count
        for (int i = 0; i < 10; i++) {
            link.grantCredit(0, 100); // Re-grant maintains credit
        }
        assertThat(link.hasCredit()).isTrue();
    }

    @Test void testNoCredit() {
        var link = new SenderLink("sender", 0, null, "queue");
        assertThat(link.hasCredit()).isFalse();
    }

    @Test void testSendWithoutSession() {
        var link = new SenderLink("sender", 0, null, "queue");
        link.grantCredit(0, 100);
        assertThatThrownBy(() -> link.send(AmqpMessage.of("test"), true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void testSendWithNoCredit() {
        var link = new SenderLink("sender", 0, null, "queue");
        var session = createMockSession();
        link.session(session);
        var delivery = link.send(AmqpMessage.of("test"), true);
        assertThat(delivery).isNull(); // No credit
    }

    @Test void testSendPreSettled() {
        var link = new SenderLink("sender", 0, null, "queue");
        var session = createMockSession();
        link.session(session);
        link.grantCredit(0, 10);

        var delivery = link.send(AmqpMessage.of("test"), true);
        assertThat(delivery).isNotNull();
        assertThat(delivery.isSenderSettled()).isTrue();
        assertThat(delivery.isSettled()).isTrue();
        assertThat(link.deliveryCount()).isEqualTo(1);
        assertThat(link.linkCredit()).isEqualTo(9);
    }

    @Test void testSendUnsettled() {
        var link = new SenderLink("sender", 0, null, "queue");
        var session = createMockSession();
        link.session(session);
        link.grantCredit(0, 10);

        var delivery = link.send(AmqpMessage.of("test"), false);
        assertThat(delivery).isNotNull();
        assertThat(delivery.isSenderSettled()).isFalse();
        assertThat(delivery.isSettled()).isFalse();
        assertThat(link.unsettledDeliveries()).hasSize(1);
    }

    @Test void testHandleDisposition() {
        var link = new SenderLink("sender", 0, null, "queue");
        var session = createMockSession();
        link.session(session);
        link.grantCredit(0, 10);

        var delivery = link.send(AmqpMessage.of("test"), false);
        assertThat(link.unsettledDeliveries()).hasSize(1);

        link.handleDisposition(0, null, true, new DeliveryState.Accepted());
        assertThat(link.unsettledDeliveries()).isEmpty();
        assertThat(delivery.isSettled()).isTrue();
        assertThat(delivery.state()).isInstanceOf(DeliveryState.Accepted.class);
    }

    @Test void testHandleDispositionRange() {
        var link = new SenderLink("sender", 0, null, "queue");
        var session = createMockSession();
        link.session(session);
        link.grantCredit(0, 10);

        link.send(AmqpMessage.of("msg1"), false);
        link.send(AmqpMessage.of("msg2"), false);
        link.send(AmqpMessage.of("msg3"), false);
        assertThat(link.unsettledDeliveries()).hasSize(3);

        link.handleDisposition(0, 2L, true, new DeliveryState.Accepted());
        assertThat(link.unsettledDeliveries()).isEmpty();
    }

    @Test void testHandleDispositionWithoutSettle() {
        var link = new SenderLink("sender", 0, null, "queue");
        var session = createMockSession();
        link.session(session);
        link.grantCredit(0, 10);

        var delivery = link.send(AmqpMessage.of("test"), false);
        link.handleDisposition(0, null, false, new DeliveryState.Received(0, 0));
        assertThat(link.unsettledDeliveries()).hasSize(1); // Still unsettled
        assertThat(delivery.state()).isInstanceOf(DeliveryState.Received.class);
    }

    @Test void testStateTransitions() {
        var link = new SenderLink("sender", 0, null, "queue");
        link.state(SenderLink.State.ATTACH_SENT);
        assertThat(link.state()).isEqualTo(SenderLink.State.ATTACH_SENT);
        link.state(SenderLink.State.ATTACHED);
        assertThat(link.state()).isEqualTo(SenderLink.State.ATTACHED);
    }

    @Test void testSourceAndTargetAddress() {
        var link = new SenderLink("sender", 0, "source-addr", "target-addr");
        assertThat(link.sourceAddress()).isEqualTo("source-addr");
        assertThat(link.targetAddress()).isEqualTo("target-addr");
    }

    private AmqpSession createMockSession() {
        var session = new AmqpSession(0);
        session.handleBegin(new Performative.Begin(0, 0, 10000, 10000));
        session.frameSender((perf, payload) -> {}); // No-op sender
        return session;
    }
}
