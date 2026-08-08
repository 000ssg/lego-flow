package ssg.legoflow.messaging.amqp.delivery;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DeliveryStateCodecTest {

    @Test void testEncodeDecodeReceived() {
        var state = new DeliveryState.Received(42L, 100L);
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.Received.class);
        var received = (DeliveryState.Received) decoded;
        assertThat(received.sectionNumber()).isEqualTo(42L);
        assertThat(received.sectionOffset()).isEqualTo(100L);
    }

    @Test void testEncodeDecodeAccepted() {
        var state = new DeliveryState.Accepted();
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.Accepted.class);
    }

    @Test void testEncodeDecodeRejectedWithErrorCondition() {
        var state = new DeliveryState.Rejected("amqp:undeclared", "something wrong");
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.Rejected.class);
    }

    @Test void testEncodeDecodeRejectedNoError() {
        var state = new DeliveryState.Rejected(null, null);
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.Rejected.class);
    }

    @Test void testEncodeDecodeReleased() {
        var state = new DeliveryState.Released();
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.Released.class);
    }

    @Test void testEncodeDecodeModifiedNoAnnotations() {
        var state = new DeliveryState.Modified(true, false, null);
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.Modified.class);
        var modified = (DeliveryState.Modified) decoded;
        assertThat(modified.deliveryFailed()).isTrue();
    }

    @Test void testEncodeDecodeModifiedWithAnnotations() {
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("key1", "value1");
        
        var state = new DeliveryState.Modified(false, true, annotations);
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.Modified.class);
    }

    @Test void testEncodeDecodeTransactionalState() {
        byte[] txnId = new byte[]{1, 2, 3};
        var state = new DeliveryState.TransactionalState(txnId, null);
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.TransactionalState.class);
    }

    @Test void testEncodeDecodeTransactionalWithOutcome() {
        byte[] txnId = new byte[]{4, 5, 6};
        var state = new DeliveryState.TransactionalState(txnId, new DeliveryState.Accepted());
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.TransactionalState.class);
    }

    @Test void testRoundTripAllDeliveryStates() {
        for (var state : java.util.List.of(
                new DeliveryState.Accepted(),
                new DeliveryState.Released(),
                new DeliveryState.Received(1L, 2L),
                new DeliveryState.Rejected("error", null),
                new DeliveryState.Modified(true, false, null)
        )) {
            var described = DeliveryStateCodec.encode(state);
            var decoded = DeliveryStateCodec.decode(described);
            assertThat(decoded).isInstanceOf(state.getClass());
        }
    }

    @Test void testReceivedZeroValues() {
        var state = new DeliveryState.Received(0L, 0L);
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        var received = (DeliveryState.Received) decoded;
        assertThat(received.sectionNumber()).isEqualTo(0L);
    }

    @Test void testModifiedEmptyAnnotations() {
        Map<String, Object> empty = new LinkedHashMap<>();
        var state = new DeliveryState.Modified(false, false, empty);
        var described = DeliveryStateCodec.encode(state);
        assertThatCode(() -> DeliveryStateCodec.decode(described))
                .doesNotThrowAnyException();
    }

    @Test void testTransactionalWithNestedReceivedOutcome() {
        byte[] txnId = new byte[]{10, 20, 30};
        var inner = new DeliveryState.Received(5L, 10L);
        var state = new DeliveryState.TransactionalState(txnId, inner);
        
        var described = DeliveryStateCodec.encode(state);
        var decoded = DeliveryStateCodec.decode(described);
        assertThat(decoded).isInstanceOf(DeliveryState.TransactionalState.class);
    }

    @Test void testTransactionalNullOutcome() {
        byte[] txnId = new byte[]{1, 2};
        var state = new DeliveryState.TransactionalState(txnId, null);
        var described = DeliveryStateCodec.encode(state);
        
        assertThatCode(() -> DeliveryStateCodec.decode(described))
                .doesNotThrowAnyException();
    }

    @Test void testEncodeMethodDefaultImplementation() {
        var state = new DeliveryState.Received(1L, 2L);
        var described = state.encode(); // Uses the default encode() method
        assertThat(described).isNotNull();
    }
}
