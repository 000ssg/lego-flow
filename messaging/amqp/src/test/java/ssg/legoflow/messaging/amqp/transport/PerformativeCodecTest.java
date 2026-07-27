package ssg.legoflow.messaging.amqp.transport;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.messaging.amqp.types.Descriptors;
import ssg.legoflow.messaging.amqp.types.TypeCodec;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PerformativeCodec} — performative encoding/decoding.
 */
class PerformativeCodecTest {

    // ---- Open ----

    @Test void testOpenMinimal() {
        var open = new Performative.Open("container-1");
        var described = PerformativeCodec.encode(open);
        var decoded = (Performative.Open) PerformativeCodec.decode(described);
        assertThat(decoded.containerId()).isEqualTo("container-1");
        assertThat(decoded.maxFrameSize()).isEqualTo(0xFFFFFFFFL);
        assertThat(decoded.channelMax()).isEqualTo(65535);
    }

    @Test void testOpenFull() {
        var open = new Performative.Open("container-2", "example.com", 65536, 100, 30000,
                List.of("cap1", "cap2"), List.of("cap3"), Map.of("key", "value"));
        var described = PerformativeCodec.encode(open);
        var decoded = (Performative.Open) PerformativeCodec.decode(described);
        assertThat(decoded.containerId()).isEqualTo("container-2");
        assertThat(decoded.hostname()).isEqualTo("example.com");
        assertThat(decoded.maxFrameSize()).isEqualTo(65536);
        assertThat(decoded.channelMax()).isEqualTo(100);
        assertThat(decoded.idleTimeout()).isEqualTo(30000);
    }

    @Test void testOpenDescriptorCode() {
        var open = new Performative.Open("test");
        var described = PerformativeCodec.encode(open);
        assertThat(TypeCodec.toLong(described.descriptor())).isEqualTo(Descriptors.OPEN);
    }

    // ---- Begin ----

    @Test void testBeginInitiating() {
        var begin = new Performative.Begin(null, 0, 2048, 2048);
        var described = PerformativeCodec.encode(begin);
        var decoded = (Performative.Begin) PerformativeCodec.decode(described);
        assertThat(decoded.remoteChannel()).isNull();
        assertThat(decoded.nextOutgoingId()).isEqualTo(0);
        assertThat(decoded.incomingWindow()).isEqualTo(2048);
        assertThat(decoded.outgoingWindow()).isEqualTo(2048);
    }

    @Test void testBeginResponse() {
        var begin = new Performative.Begin(5, 100, 4096, 4096);
        var described = PerformativeCodec.encode(begin);
        var decoded = (Performative.Begin) PerformativeCodec.decode(described);
        assertThat(decoded.remoteChannel()).isEqualTo(5);
        assertThat(decoded.nextOutgoingId()).isEqualTo(100);
    }

    @Test void testBeginDescriptorCode() {
        var begin = new Performative.Begin(null, 0, 100, 100);
        var described = PerformativeCodec.encode(begin);
        assertThat(TypeCodec.toLong(described.descriptor())).isEqualTo(Descriptors.BEGIN);
    }

    // ---- Attach ----

    @Test void testAttachSender() {
        var attach = new Performative.Attach("link-1", 0, false,
                PerformativeCodec.encodeSource("queue-a"),
                PerformativeCodec.encodeTarget("queue-b"));
        var described = PerformativeCodec.encode(attach);
        var decoded = (Performative.Attach) PerformativeCodec.decode(described);
        assertThat(decoded.name()).isEqualTo("link-1");
        assertThat(decoded.handle()).isEqualTo(0);
        assertThat(decoded.role()).isFalse(); // sender
    }

    @Test void testAttachReceiver() {
        var attach = new Performative.Attach("link-2", 1, true,
                PerformativeCodec.encodeSource("queue-c"),
                PerformativeCodec.encodeTarget("queue-d"));
        var described = PerformativeCodec.encode(attach);
        var decoded = (Performative.Attach) PerformativeCodec.decode(described);
        assertThat(decoded.name()).isEqualTo("link-2");
        assertThat(decoded.role()).isTrue(); // receiver
    }

    @Test void testAttachDescriptorCode() {
        var attach = new Performative.Attach("test", 0, false, null, null);
        var described = PerformativeCodec.encode(attach);
        assertThat(TypeCodec.toLong(described.descriptor())).isEqualTo(Descriptors.ATTACH);
    }

    @Test void testAttachSettleModes() {
        var attach = new Performative.Attach("link", 0, false, 1, 1,
                null, null, 0L, 1048576, List.of(), List.of(), Map.of());
        var described = PerformativeCodec.encode(attach);
        var decoded = (Performative.Attach) PerformativeCodec.decode(described);
        assertThat(decoded.sndSettleMode()).isEqualTo(1);
        assertThat(decoded.rcvSettleMode()).isEqualTo(1);
        assertThat(decoded.maxMessageSize()).isEqualTo(1048576);
    }

    // ---- Flow ----

    @Test void testFlowSessionLevel() {
        var flow = new Performative.Flow(0L, 2048, 0, 2048,
                null, null, null, null, false, false, Map.of());
        var described = PerformativeCodec.encode(flow);
        var decoded = (Performative.Flow) PerformativeCodec.decode(described);
        assertThat(decoded.incomingWindow()).isEqualTo(2048);
        assertThat(decoded.handle()).isNull();
    }

    @Test void testFlowLinkLevel() {
        var flow = new Performative.Flow(10L, 2048, 5, 2048,
                0L, 5L, 100L, 50L, false, true, Map.of());
        var described = PerformativeCodec.encode(flow);
        var decoded = (Performative.Flow) PerformativeCodec.decode(described);
        assertThat(decoded.handle()).isEqualTo(0L);
        assertThat(decoded.deliveryCount()).isEqualTo(5L);
        assertThat(decoded.linkCredit()).isEqualTo(100L);
        assertThat(decoded.available()).isEqualTo(50L);
        assertThat(decoded.echo()).isTrue();
    }

    @Test void testFlowDrain() {
        var flow = new Performative.Flow(0L, 2048, 0, 2048,
                0L, 0L, 0L, null, true, false, Map.of());
        var described = PerformativeCodec.encode(flow);
        var decoded = (Performative.Flow) PerformativeCodec.decode(described);
        assertThat(decoded.drain()).isTrue();
    }

    @Test void testFlowDescriptorCode() {
        var flow = new Performative.Flow(0L, 100, 0, 100,
                null, null, null, null, false, false, Map.of());
        var described = PerformativeCodec.encode(flow);
        assertThat(TypeCodec.toLong(described.descriptor())).isEqualTo(Descriptors.FLOW);
    }

    // ---- Transfer ----

    @Test void testTransferMinimal() {
        var transfer = new Performative.Transfer(0, 0L, new byte[]{1}, true);
        var described = PerformativeCodec.encode(transfer);
        var decoded = (Performative.Transfer) PerformativeCodec.decode(described);
        assertThat(decoded.handle()).isEqualTo(0);
        assertThat(decoded.deliveryId()).isEqualTo(0L);
        assertThat(decoded.settled()).isTrue();
    }

    @Test void testTransferUnsettled() {
        var transfer = new Performative.Transfer(1, 5L, new byte[]{1, 2, 3, 4}, false);
        var described = PerformativeCodec.encode(transfer);
        var decoded = (Performative.Transfer) PerformativeCodec.decode(described);
        assertThat(decoded.handle()).isEqualTo(1);
        assertThat(decoded.deliveryId()).isEqualTo(5L);
        assertThat(decoded.deliveryTag()).isEqualTo(new byte[]{1, 2, 3, 4});
        assertThat(decoded.settled()).isFalse();
    }

    @Test void testTransferFull() {
        var transfer = new Performative.Transfer(0, 10L, new byte[]{1}, 0, true,
                true, 1, null, false, false, true);
        var described = PerformativeCodec.encode(transfer);
        var decoded = (Performative.Transfer) PerformativeCodec.decode(described);
        assertThat(decoded.more()).isTrue();
        assertThat(decoded.batchable()).isTrue();
    }

    @Test void testTransferDescriptorCode() {
        var transfer = new Performative.Transfer(0, 0L, new byte[]{1}, true);
        var described = PerformativeCodec.encode(transfer);
        assertThat(TypeCodec.toLong(described.descriptor())).isEqualTo(Descriptors.TRANSFER);
    }

    // ---- Disposition ----

    @Test void testDispositionSender() {
        var disp = new Performative.Disposition(false, 0, null, true, null, false);
        var described = PerformativeCodec.encode(disp);
        var decoded = (Performative.Disposition) PerformativeCodec.decode(described);
        assertThat(decoded.role()).isFalse();
        assertThat(decoded.first()).isEqualTo(0);
        assertThat(decoded.settled()).isTrue();
    }

    @Test void testDispositionReceiver() {
        var accepted = new ssg.legoflow.messaging.amqp.delivery.DeliveryState.Accepted().encode();
        var disp = new Performative.Disposition(true, 5, 10L, true, accepted, false);
        var described = PerformativeCodec.encode(disp);
        var decoded = (Performative.Disposition) PerformativeCodec.decode(described);
        assertThat(decoded.role()).isTrue();
        assertThat(decoded.first()).isEqualTo(5);
        assertThat(decoded.last()).isEqualTo(10L);
    }

    @Test void testDispositionDescriptorCode() {
        var disp = new Performative.Disposition(true, 0, null, true, null, false);
        var described = PerformativeCodec.encode(disp);
        assertThat(TypeCodec.toLong(described.descriptor())).isEqualTo(Descriptors.DISPOSITION);
    }

    // ---- Detach ----

    @Test void testDetachGraceful() {
        var detach = new Performative.Detach(0, true);
        var described = PerformativeCodec.encode(detach);
        var decoded = (Performative.Detach) PerformativeCodec.decode(described);
        assertThat(decoded.handle()).isEqualTo(0);
        assertThat(decoded.closed()).isTrue();
        assertThat(decoded.error()).isNull();
    }

    @Test void testDetachWithError() {
        var error = PerformativeCodec.encodeError("amqp:link:detach-forced", "link force-detached");
        var detach = new Performative.Detach(1, true, error);
        var described = PerformativeCodec.encode(detach);
        var decoded = (Performative.Detach) PerformativeCodec.decode(described);
        assertThat(decoded.handle()).isEqualTo(1);
        assertThat(decoded.error()).isNotNull();
    }

    @Test void testDetachDescriptorCode() {
        var detach = new Performative.Detach(0, false);
        var described = PerformativeCodec.encode(detach);
        assertThat(TypeCodec.toLong(described.descriptor())).isEqualTo(Descriptors.DETACH);
    }

    // ---- End ----

    @Test void testEndGraceful() {
        var end = new Performative.End();
        var described = PerformativeCodec.encode(end);
        var decoded = (Performative.End) PerformativeCodec.decode(described);
        assertThat(decoded.error()).isNull();
    }

    @Test void testEndWithError() {
        var error = PerformativeCodec.encodeError("amqp:internal-error", "something broke");
        var end = new Performative.End(error);
        var described = PerformativeCodec.encode(end);
        var decoded = (Performative.End) PerformativeCodec.decode(described);
        assertThat(decoded.error()).isNotNull();
    }

    @Test void testEndDescriptorCode() {
        var end = new Performative.End();
        var described = PerformativeCodec.encode(end);
        assertThat(TypeCodec.toLong(described.descriptor())).isEqualTo(Descriptors.END);
    }

    // ---- Close ----

    @Test void testCloseGraceful() {
        var close = new Performative.Close();
        var described = PerformativeCodec.encode(close);
        var decoded = (Performative.Close) PerformativeCodec.decode(described);
        assertThat(decoded.error()).isNull();
    }

    @Test void testCloseWithError() {
        var error = PerformativeCodec.encodeError("amqp:connection:forced", "shutdown");
        var close = new Performative.Close(error);
        var described = PerformativeCodec.encode(close);
        var decoded = (Performative.Close) PerformativeCodec.decode(described);
        assertThat(decoded.error()).isNotNull();
    }

    @Test void testCloseDescriptorCode() {
        var close = new Performative.Close();
        var described = PerformativeCodec.encode(close);
        assertThat(TypeCodec.toLong(described.descriptor())).isEqualTo(Descriptors.CLOSE);
    }

    // ---- Source/Target ----

    @Test void testEncodeSource() {
        var source = PerformativeCodec.encodeSource("my-queue");
        assertThat(TypeCodec.toLong(source.descriptor())).isEqualTo(Descriptors.SOURCE);
        assertThat(PerformativeCodec.extractAddress(source)).isEqualTo("my-queue");
    }

    @Test void testEncodeTarget() {
        var target = PerformativeCodec.encodeTarget("my-topic");
        assertThat(TypeCodec.toLong(target.descriptor())).isEqualTo(Descriptors.TARGET);
        assertThat(PerformativeCodec.extractAddress(target)).isEqualTo("my-topic");
    }

    @Test void testEncodeNullSource() {
        var source = PerformativeCodec.encodeSource(null);
        assertThat(PerformativeCodec.extractAddress(source)).isNull();
    }

    @Test void testExtractAddressFromNonDescribed() {
        assertThat(PerformativeCodec.extractAddress(new AmqpType.Null())).isNull();
    }

    // ---- Error ----

    @Test void testEncodeError() {
        var error = PerformativeCodec.encodeError("amqp:not-found", "Resource not found");
        assertThat(TypeCodec.toLong(error.descriptor())).isEqualTo(Descriptors.ERROR);
    }

    @Test void testDescriptorOf() {
        var open = PerformativeCodec.encode(new Performative.Open("test"));
        assertThat(PerformativeCodec.descriptorOf(open)).isEqualTo(Descriptors.OPEN);
    }

    @Test void testDescriptorOfNonDescribed() {
        assertThat(PerformativeCodec.descriptorOf(new AmqpType.Int(42))).isEqualTo(-1);
    }
}
