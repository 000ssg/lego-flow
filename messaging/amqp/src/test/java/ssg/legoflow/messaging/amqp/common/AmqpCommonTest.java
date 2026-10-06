package ssg.legoflow.messaging.amqp.common;

import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.sasl.ExternalMechanism;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Coverage tests for small AMQP classes that aren't hit by integration tests.
 */
class AmqpCommonTest {

    // --- AmqpException ---

    @Test void amqpExceptionWithConditionAndDescription() {
        var ex = new AmqpException("amqp:decode-error", "bad frame");
        assertThat(ex.getMessage()).isEqualTo("bad frame");
        assertThat(ex.condition()).isEqualTo("amqp:decode-error");
    }

    @Test void amqpExceptionWithCause() {
        var cause = new NullPointerException("root");
        var ex = new AmqpException("amqp:internal-error", "fail", cause);
        assertThat(ex.getMessage()).isEqualTo("fail");
        assertThat(ex.condition()).isEqualTo("amqp:internal-error");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    // --- AmqpEventListener ---

    @Test void noOpListenerDoesNothing() {
        AmqpEventListener noOp = AmqpEventListener.noOp();
        noOp.onEvent(AmqpEventListener.EventType.CONNECTION_STARTED, "conn-1", null);
    }

    @Test void latchOnFirstCountsDownOnMatchingEvent() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = AmqpEventListener.latchOnFirst(latch, AmqpEventListener.EventType.MESSAGE_RECEIVED);

        // Wrong event — latch should NOT count down
        listener.onEvent(AmqpEventListener.EventType.CONNECTION_STARTED, "c1", null);
        assertThat(latch.getCount()).isEqualTo(1);

        // Matching event — latch counts down
        listener.onEvent(AmqpEventListener.EventType.MESSAGE_RECEIVED, "c1", "link-1");
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
    }

    @Test void eventTypeValues() {
        var values = AmqpEventListener.EventType.values();
        assertThat(values).containsExactly(
            AmqpEventListener.EventType.CONNECTION_STARTED,
            AmqpEventListener.EventType.CONNECTION_OPENED,
            AmqpEventListener.EventType.SESSION_CREATED,
            AmqpEventListener.EventType.LINK_ATTACHED,
            AmqpEventListener.EventType.LINK_DETACHED,
            AmqpEventListener.EventType.CONNECTION_CLOSING,
            AmqpEventListener.EventType.MESSAGE_RECEIVED,
            AmqpEventListener.EventType.MESSAGE_SENT
        );
    }

    @Test void noOpIsStaticInstance() {
        assertThat(AmqpEventListener.noOp()).isSameAs(AmqpEventListener.noOp());
        assertThat(AmqpEventListener.noOp()).isSameAs(AmqpEventListener.NO_OP);
    }

    // --- DeliveryState ---

    @Test void acceptedState() {
        var accepted = new DeliveryState.Accepted();
        assertThat(accepted).isNotNull();
    }

    @Test void releasedState() {
        var released = new DeliveryState.Released();
        assertThat(released).isNotNull();
    }

    @Test void receivedState() {
        var received = new DeliveryState.Received(1, 42);
        assertThat(received.sectionNumber()).isEqualTo(1);
        assertThat(received.sectionOffset()).isEqualTo(42);
    }

    @Test void rejectedStateWithConditionOnly() {
        var rejected = new DeliveryState.Rejected("amqp:undeclared-codec");
        assertThat(rejected.errorCondition()).isEqualTo("amqp:undeclared-codec");
        assertThat(rejected.errorDescription()).isNull();
    }

    @Test void rejectedStateWithDescription() {
        var rejected = new DeliveryState.Rejected("amqp:decode-error", "invalid type");
        assertThat(rejected.errorCondition()).isEqualTo("amqp:decode-error");
        assertThat(rejected.errorDescription()).isEqualTo("invalid type");
    }

    @Test void modifiedState() {
        var modified = new DeliveryState.Modified(true, false, Map.of("x", 1));
        assertThat(modified.deliveryFailed()).isTrue();
        assertThat(modified.undeliverableHere()).isFalse();
    }

    @Test void transactionalState() {
        var txn = new DeliveryState.TransactionalState(new byte[]{1, 2}, new DeliveryState.Accepted());
        assertThat(txn.txnId()).containsExactly((byte)1, (byte)2);
        assertThat(txn.outcome()).isInstanceOf(DeliveryState.Accepted.class);
    }

    // --- AmqpResult ---

    @Test void amqpResultOk() {
        var result = AmqpClientService.AmqpResult.ok("test-addr", ByteBuffer.wrap("data".getBytes()));
        assertThat(result.success()).isTrue();
        assertThat(result.address()).isEqualTo("test-addr");
        assertThat(result.payload()).isNotNull();
    }

    @Test void amqpResultError() {
        var result = AmqpClientService.AmqpResult.error("link closed");
        assertThat(result.success()).isFalse();
        assertThat(result.address()).isNull();
        assertThat(result.payload()).isNull();
    }

    // --- ExternalMechanism ---

    @Test void externalMechanismName() {
        var mech = new ExternalMechanism();
        assertThat(mech.name()).isEqualTo("EXTERNAL");
    }

    @Test void externalMechanismInitialResponse() {
        var mech = new ExternalMechanism();
        assertThat(mech.initialResponse()).isEmpty();
    }

    @Test void externalMechanismRespond() {
        var mech = new ExternalMechanism();
        assertThat(mech.respond(new byte[]{1, 2, 3})).isEmpty();
    }
}
