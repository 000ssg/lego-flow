package ssg.legoflow.messaging.amqp.transport;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.common.AmqpException;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.messaging.amqp.types.Descriptors;
import java.nio.ByteBuffer;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link FrameCodec} — AMQP frame encoding/decoding.
 */
class FrameCodecTest {

    @Test void testHeartbeatFrameRoundTrip() {
        AmqpFrame heartbeat = AmqpFrame.heartbeat();
        ByteBuffer buf = FrameCodec.encode(heartbeat, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        buf.rewind();
        AmqpFrame decoded = FrameCodec.decode(buf);
        assertThat(decoded.isHeartbeat()).isTrue();
    }

    @Test void testHeartbeatFrameSize() {
        AmqpFrame heartbeat = AmqpFrame.heartbeat();
        ByteBuffer buf = FrameCodec.encode(heartbeat, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        assertThat(buf.remaining()).isEqualTo(AmqpConstants.FRAME_HEADER_SIZE);
    }

    @Test void testFrameWithPerformativeRoundTrip() {
        var open = PerformativeCodec.encode(new Performative.Open("test-container"));
        var frame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_AMQP, open);
        ByteBuffer buf = FrameCodec.encode(frame, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        buf.rewind();
        AmqpFrame decoded = FrameCodec.decode(buf);
        assertThat(decoded.isHeartbeat()).isFalse();
        assertThat(decoded.channel()).isEqualTo(0);
        assertThat(decoded.type()).isEqualTo(AmqpConstants.FRAME_TYPE_AMQP);
        assertThat(decoded.performative()).isInstanceOf(AmqpType.Described.class);
    }

    @Test void testFrameChannel() {
        var close = PerformativeCodec.encode(new Performative.Close());
        var frame = new AmqpFrame(42, AmqpConstants.FRAME_TYPE_AMQP, close);
        ByteBuffer buf = FrameCodec.encode(frame, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        buf.rewind();
        AmqpFrame decoded = FrameCodec.decode(buf);
        assertThat(decoded.channel()).isEqualTo(42);
    }

    @Test void testFrameWithPayload() {
        var transfer = PerformativeCodec.encode(
                new Performative.Transfer(0, 0L, new byte[]{1, 2}, true));
        byte[] payload = {10, 20, 30, 40, 50};
        var frame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_AMQP, transfer, ByteBuffer.wrap(payload));
        ByteBuffer buf = FrameCodec.encode(frame, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        buf.rewind();
        AmqpFrame decoded = FrameCodec.decode(buf);
        assertThat(decoded.payload()).isNotNull();
        byte[] decodedPayload = new byte[decoded.payload().remaining()];
        decoded.payload().get(decodedPayload);
        assertThat(decodedPayload).isEqualTo(payload);
    }

    @Test void testFrameTypeAmqp() {
        var end = PerformativeCodec.encode(new Performative.End());
        var frame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_AMQP, end);
        ByteBuffer buf = FrameCodec.encode(frame, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        buf.rewind();
        AmqpFrame decoded = FrameCodec.decode(buf);
        assertThat(decoded.type()).isEqualTo(AmqpConstants.FRAME_TYPE_AMQP);
    }

    @Test void testFrameTypeSasl() {
        var mechs = new AmqpType.Described(
                new AmqpType.ULong(Descriptors.SASL_MECHANISMS),
                new AmqpType.AmqpList(List.of(
                        new AmqpType.AmqpArray(List.of(new AmqpType.Symbol("ANONYMOUS")))
                ))
        );
        var frame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_SASL, mechs);
        ByteBuffer buf = FrameCodec.encode(frame, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        buf.rewind();
        AmqpFrame decoded = FrameCodec.decode(buf);
        assertThat(decoded.type()).isEqualTo(AmqpConstants.FRAME_TYPE_SASL);
    }

    @Test void testHasCompleteFrame() {
        var open = PerformativeCodec.encode(new Performative.Open("test"));
        var frame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_AMQP, open);
        ByteBuffer buf = FrameCodec.encode(frame, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        buf.rewind();
        assertThat(FrameCodec.hasCompleteFrame(buf)).isTrue();
    }

    @Test void testHasCompleteFrameIncomplete() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(100); // Claims to be 100 bytes but only 4 available
        buf.flip();
        assertThat(FrameCodec.hasCompleteFrame(buf)).isFalse();
    }

    @Test void testHasCompleteFrameTooSmall() {
        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.putShort((short) 0);
        buf.flip();
        assertThat(FrameCodec.hasCompleteFrame(buf)).isFalse();
    }

    @Test void testPeekFrameSize() {
        ByteBuffer buf = ByteBuffer.allocate(10);
        buf.putInt(42);
        buf.flip();
        assertThat(FrameCodec.peekFrameSize(buf)).isEqualTo(42);
        assertThat(buf.position()).isEqualTo(0); // Position unchanged
    }

    @Test void testFrameSizeExceedsMax() {
        var open = PerformativeCodec.encode(new Performative.Open("test"));
        var frame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_AMQP, open);
        assertThatThrownBy(() -> FrameCodec.encode(frame, 8)) // Too small
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("exceeds max");
    }

    @Test void testDecodeInsufficientData() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(0);
        buf.flip();
        assertThatThrownBy(() -> FrameCodec.decode(buf))
                .isInstanceOf(AmqpException.class);
    }

    @Test void testFrameHeaderDoff() {
        var open = PerformativeCodec.encode(new Performative.Open("test"));
        var frame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_AMQP, open);
        ByteBuffer buf = FrameCodec.encode(frame, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        // DOFF is at position 4, should be 2 (8 bytes / 4)
        assertThat(buf.get(4)).isEqualTo((byte) 2);
    }

    @Test void testOpenPerformativeFrameRoundTrip() {
        var perf = new Performative.Open("my-container", "localhost", 65536, 255, 30000,
                List.of("cap1"), List.of("cap2"), java.util.Map.of());
        var described = PerformativeCodec.encode(perf);
        var frame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_AMQP, described);
        ByteBuffer buf = FrameCodec.encode(frame, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        buf.rewind();
        AmqpFrame decoded = FrameCodec.decode(buf);
        assertThat(decoded.performative()).isInstanceOf(AmqpType.Described.class);
        var decodedPerf = PerformativeCodec.decode((AmqpType.Described) decoded.performative());
        assertThat(decodedPerf).isInstanceOf(Performative.Open.class);
        var openPerf = (Performative.Open) decodedPerf;
        assertThat(openPerf.containerId()).isEqualTo("my-container");
    }

    @Test void testBeginPerformativeFrameRoundTrip() {
        var perf = new Performative.Begin(null, 0, 2048, 2048);
        var described = PerformativeCodec.encode(perf);
        var frame = new AmqpFrame(1, AmqpConstants.FRAME_TYPE_AMQP, described);
        ByteBuffer buf = FrameCodec.encode(frame, AmqpConstants.DEFAULT_MAX_FRAME_SIZE);
        buf.rewind();
        AmqpFrame decoded = FrameCodec.decode(buf);
        var decodedPerf = PerformativeCodec.decode((AmqpType.Described) decoded.performative());
        assertThat(decodedPerf).isInstanceOf(Performative.Begin.class);
        assertThat(decoded.channel()).isEqualTo(1);
    }
}
