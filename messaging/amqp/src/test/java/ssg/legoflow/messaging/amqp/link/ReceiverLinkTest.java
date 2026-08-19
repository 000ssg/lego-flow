package ssg.legoflow.messaging.amqp.link;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.transport.Performative;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ReceiverLink} — receiver link and credit management.
 */
class ReceiverLinkTest {

    @Test void testInitialState() {
        var link = new ReceiverLink("receiver-1", 0, "queue", null);
        assertThat(link.name()).isEqualTo("receiver-1");
        assertThat(link.handle()).isEqualTo(0);
        assertThat(link.state()).isEqualTo(ReceiverLink.State.DETACHED);
        assertThat(link.deliveryCount()).isEqualTo(0);
    }

    @Test void testCreateAttach() {
        var link = new ReceiverLink("receiver-1", 0, "src", "tgt");
        var attach = link.createAttach();
        assertThat(attach.name()).isEqualTo("receiver-1");
        assertThat(attach.role()).isTrue(); // receiver
    }

    @Test void testHandleTransfer() throws InterruptedException {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        var message = AmqpMessage.of("Hello");
        link.handleTransfer(0, new byte[]{1}, message, true);

        assertThat(link.deliveryCount()).isEqualTo(1);
        assertThat(link.available()).isEqualTo(1);

        Delivery delivery = link.receive(1, TimeUnit.SECONDS);
        assertThat(delivery).isNotNull();
        assertThat(delivery.message().bodyAsString()).isEqualTo("Hello");
        assertThat(delivery.isSenderSettled()).isTrue();
    }

    @Test void testHandleTransferUnsettled() throws InterruptedException {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        var message = AmqpMessage.of("Hello");
        link.handleTransfer(0, new byte[]{1}, message, false);

        assertThat(link.unsettledDeliveries()).hasSize(1);

        Delivery delivery = link.receive(1, TimeUnit.SECONDS);
        assertThat(delivery).isNotNull();
        assertThat(delivery.isSenderSettled()).isFalse();
    }

    @Test void testAccept() {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        var session = createMockSession();
        link.session(session);
        link.handleTransfer(0, new byte[]{1}, AmqpMessage.of("test"), false);

        link.accept(0);
        assertThat(link.unsettledDeliveries()).isEmpty();
    }

    @Test void testReject() {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        var session = createMockSession();
        link.session(session);
        link.handleTransfer(0, new byte[]{1}, AmqpMessage.of("test"), false);

        link.reject(0, "amqp:not-found");
        assertThat(link.unsettledDeliveries()).isEmpty();
    }

    @Test void testRelease() {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        var session = createMockSession();
        link.session(session);
        link.handleTransfer(0, new byte[]{1}, AmqpMessage.of("test"), false);

        link.release(0);
        assertThat(link.unsettledDeliveries()).isEmpty();
    }

    @Test void testSettle() {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        var session = createMockSession();
        link.session(session);
        link.handleTransfer(0, new byte[]{1}, AmqpMessage.of("test"), false);

        var modified = new DeliveryState.Modified(true, false, java.util.Map.of());
        link.settle(0, modified);
        assertThat(link.unsettledDeliveries()).isEmpty();
    }

    @Test void testMessageHandler() {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        var received = new java.util.concurrent.atomic.AtomicReference<Delivery>();
        link.messageHandler(received::set);

        link.handleTransfer(0, new byte[]{1}, AmqpMessage.of("callback test"), true);
        assertThat(received.get()).isNotNull();
        assertThat(received.get().message().bodyAsString()).isEqualTo("callback test");
    }

    @Test void testMultipleMessages() throws InterruptedException {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        for (int i = 0; i < 5; i++) {
            link.handleTransfer(i, new byte[]{(byte) i}, AmqpMessage.of("msg-" + i), true);
        }

        assertThat(link.available()).isEqualTo(5);
        assertThat(link.deliveryCount()).isEqualTo(5);

        for (int i = 0; i < 5; i++) {
            Delivery d = link.receive(1, TimeUnit.SECONDS);
            assertThat(d).isNotNull();
            assertThat(d.message().bodyAsString()).isEqualTo("msg-" + i);
        }
    }

    @Test void testReceiveTimeout() throws InterruptedException {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        Delivery d = link.receive(100, TimeUnit.MILLISECONDS);
        assertThat(d).isNull();
    }

    @Test void testStateTransitions() {
        var link = new ReceiverLink("receiver", 0, "queue", null);
        link.state(ReceiverLink.State.ATTACH_SENT);
        assertThat(link.state()).isEqualTo(ReceiverLink.State.ATTACH_SENT);
        link.state(ReceiverLink.State.ATTACHED);
        assertThat(link.state()).isEqualTo(ReceiverLink.State.ATTACHED);
    }

    @Test void testSourceAndTargetAddress() {
        var link = new ReceiverLink("receiver", 0, "source-addr", "target-addr");
        assertThat(link.sourceAddress()).isEqualTo("source-addr");
        assertThat(link.targetAddress()).isEqualTo("target-addr");
    }

    private AmqpSession createMockSession() {
        var session = new AmqpSession(0);
        session.handleBegin(new Performative.Begin(0, 0, 10000, 10000));
        session.frameSender((perf, payload) -> {}); // No-op
        return session;
    }
}
