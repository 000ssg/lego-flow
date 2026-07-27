package ssg.legoflow.http3.quic;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link QuicStream} — state transitions, flow control,
 * ID type detection, and send/receive operations.
 *
 * @since 1.0.0
 */
class QuicStreamTest {

    @Test
    void testInitialState() {
        var stream = new QuicStream(0, 65535, 65535);

        assertThat(stream.state()).isEqualTo(QuicStreamState.IDLE);
        assertThat(stream.streamId()).isEqualTo(0);
        assertThat(stream.sendWindowSize()).isEqualTo(65535);
        assertThat(stream.receiveWindowSize()).isEqualTo(65535);
    }

    @Test
    void testTransitionToOpen() {
        var stream = new QuicStream(0, 65535, 65535);

        stream.transitionTo(QuicStreamState.OPEN);

        assertThat(stream.state()).isEqualTo(QuicStreamState.OPEN);
        assertThat(stream.isOpen()).isTrue();
    }

    @Test
    void testTransitionToHalfClosedLocal() {
        var stream = new QuicStream(0, 65535, 65535);
        stream.transitionTo(QuicStreamState.OPEN);

        stream.transitionTo(QuicStreamState.HALF_CLOSED_LOCAL);

        assertThat(stream.state()).isEqualTo(QuicStreamState.HALF_CLOSED_LOCAL);
        assertThat(stream.isOpen()).isTrue();
    }

    @Test
    void testTransitionToHalfClosedRemote() {
        var stream = new QuicStream(0, 65535, 65535);
        stream.transitionTo(QuicStreamState.OPEN);

        stream.transitionTo(QuicStreamState.HALF_CLOSED_REMOTE);

        assertThat(stream.state()).isEqualTo(QuicStreamState.HALF_CLOSED_REMOTE);
        assertThat(stream.isOpen()).isTrue();
    }

    @Test
    void testTransitionToClosed() {
        var stream = new QuicStream(0, 65535, 65535);
        stream.transitionTo(QuicStreamState.OPEN);

        stream.transitionTo(QuicStreamState.CLOSED);

        assertThat(stream.state()).isEqualTo(QuicStreamState.CLOSED);
        assertThat(stream.isClosed()).isTrue();
        assertThat(stream.isOpen()).isFalse();
    }

    @Test
    void testInvalidTransition() {
        var stream = new QuicStream(0, 65535, 65535);
        stream.transitionTo(QuicStreamState.OPEN);
        stream.transitionTo(QuicStreamState.CLOSED);

        assertThatThrownBy(() -> stream.transitionTo(QuicStreamState.OPEN))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testSendConsumesSendWindow() {
        var stream = new QuicStream(0, 1000, 1000);
        stream.transitionTo(QuicStreamState.OPEN);

        boolean result = stream.send(ByteBuffer.allocate(100));

        assertThat(result).isTrue();
        assertThat(stream.sendWindowSize()).isEqualTo(900);
    }

    @Test
    void testSendExceedingWindow() {
        var stream = new QuicStream(0, 100, 100);
        stream.transitionTo(QuicStreamState.OPEN);

        boolean result = stream.send(ByteBuffer.allocate(200));

        assertThat(result).isFalse();
        assertThat(stream.sendWindowSize()).isEqualTo(100);
    }

    @Test
    void testSendWhenNotOpen() {
        var stream = new QuicStream(0, 1000, 1000);

        assertThatThrownBy(() -> stream.send(ByteBuffer.allocate(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testReceiveConsumesReceiveWindow() {
        var stream = new QuicStream(0, 1000, 1000);
        stream.transitionTo(QuicStreamState.OPEN);

        stream.receive(ByteBuffer.wrap("hello".getBytes()));

        assertThat(stream.receiveWindowSize()).isEqualTo(995);
    }

    @Test
    void testReceiveBuffersData() {
        var stream = new QuicStream(0, 1000, 1000);
        stream.transitionTo(QuicStreamState.OPEN);

        stream.receive(ByteBuffer.wrap("hello".getBytes()));
        stream.receive(ByteBuffer.wrap(" world".getBytes()));

        var data = stream.getAccumulatedData();
        var bytes = new byte[data.remaining()];
        data.get(bytes);
        assertThat(new String(bytes)).isEqualTo("hello world");
    }

    @Test
    void testAccumulatedDataEmpty() {
        var stream = new QuicStream(0, 1000, 1000);

        assertThat(stream.getAccumulatedData().remaining()).isEqualTo(0);
    }

    @Test
    void testResetStream() {
        var stream = new QuicStream(0, 1000, 1000);
        stream.transitionTo(QuicStreamState.OPEN);

        stream.resetStream(0);

        assertThat(stream.state()).isEqualTo(QuicStreamState.RESET_SENT);
    }

    @Test
    void testResetStreamFromClosed() {
        var stream = new QuicStream(0, 1000, 1000);
        stream.transitionTo(QuicStreamState.OPEN);
        stream.transitionTo(QuicStreamState.CLOSED);

        assertThatThrownBy(() -> stream.resetStream(0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testStopSendingWhenOpen() {
        var stream = new QuicStream(0, 1000, 1000);
        stream.transitionTo(QuicStreamState.OPEN);

        // Should not throw
        stream.stopSending(0);
    }

    @Test
    void testStopSendingWhenIdle() {
        var stream = new QuicStream(0, 1000, 1000);

        assertThatThrownBy(() -> stream.stopSending(0))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Stream ID type detection ──

    @Test
    void testClientInitiatedBidi() {
        // 0x0 = client bidi
        var stream = new QuicStream(0, 1000, 1000);

        assertThat(stream.isClientInitiated()).isTrue();
        assertThat(stream.isServerInitiated()).isFalse();
        assertThat(stream.isBidirectional()).isTrue();
        assertThat(stream.isUnidirectional()).isFalse();
    }

    @Test
    void testServerInitiatedBidi() {
        // 0x1 = server bidi
        var stream = new QuicStream(1, 1000, 1000);

        assertThat(stream.isClientInitiated()).isFalse();
        assertThat(stream.isServerInitiated()).isTrue();
        assertThat(stream.isBidirectional()).isTrue();
        assertThat(stream.isUnidirectional()).isFalse();
    }

    @Test
    void testClientInitiatedUni() {
        // 0x2 = client uni
        var stream = new QuicStream(2, 1000, 1000);

        assertThat(stream.isClientInitiated()).isTrue();
        assertThat(stream.isServerInitiated()).isFalse();
        assertThat(stream.isBidirectional()).isFalse();
        assertThat(stream.isUnidirectional()).isTrue();
    }

    @Test
    void testServerInitiatedUni() {
        // 0x3 = server uni
        var stream = new QuicStream(3, 1000, 1000);

        assertThat(stream.isClientInitiated()).isFalse();
        assertThat(stream.isServerInitiated()).isTrue();
        assertThat(stream.isBidirectional()).isFalse();
        assertThat(stream.isUnidirectional()).isTrue();
    }

    @Test
    void testHigherStreamIds() {
        // Stream ID 4 = next client bidi (4 mod 4 = 0)
        var stream4 = new QuicStream(4, 1000, 1000);
        assertThat(stream4.isClientInitiated()).isTrue();
        assertThat(stream4.isBidirectional()).isTrue();

        // Stream ID 5 = next server bidi (5 mod 4 = 1)
        var stream5 = new QuicStream(5, 1000, 1000);
        assertThat(stream5.isServerInitiated()).isTrue();
        assertThat(stream5.isBidirectional()).isTrue();
    }

    @Test
    void testAdjustSendWindow() {
        var stream = new QuicStream(0, 1000, 1000);

        stream.adjustSendWindow(500);

        assertThat(stream.sendWindowSize()).isEqualTo(1500);
    }

    @Test
    void testAdjustReceiveWindow() {
        var stream = new QuicStream(0, 1000, 1000);

        stream.adjustReceiveWindow(-200);

        assertThat(stream.receiveWindowSize()).isEqualTo(800);
    }

    @Test
    void testSendFromHalfClosedRemote() {
        var stream = new QuicStream(0, 1000, 1000);
        stream.transitionTo(QuicStreamState.OPEN);
        stream.transitionTo(QuicStreamState.HALF_CLOSED_REMOTE);

        boolean result = stream.send(ByteBuffer.allocate(50));

        assertThat(result).isTrue();
    }

    @Test
    void testReceiveFromHalfClosedLocal() {
        var stream = new QuicStream(0, 1000, 1000);
        stream.transitionTo(QuicStreamState.OPEN);
        stream.transitionTo(QuicStreamState.HALF_CLOSED_LOCAL);

        // Should not throw — receive side is still open
        stream.receive(ByteBuffer.wrap("data".getBytes()));
    }
}
