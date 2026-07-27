package ssg.legoflow.http2.stream;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

class Http2StreamTest {

    @Test
    void testInitialState() {
        var stream = new Http2Stream(1, 65535);

        assertThat(stream.streamId()).isEqualTo(1);
        assertThat(stream.state()).isEqualTo(Http2StreamState.IDLE);
        assertThat(stream.sendWindowSize()).isEqualTo(65535);
        assertThat(stream.receiveWindowSize()).isEqualTo(65535);
    }

    @Test
    void testTransitionIdleToOpen() {
        var stream = new Http2Stream(1, 65535);
        stream.transitionTo(Http2StreamState.OPEN);

        assertThat(stream.state()).isEqualTo(Http2StreamState.OPEN);
    }

    @Test
    void testTransitionOpenToHalfClosedLocal() {
        var stream = new Http2Stream(1, 65535);
        stream.transitionTo(Http2StreamState.OPEN);
        stream.transitionTo(Http2StreamState.HALF_CLOSED_LOCAL);

        assertThat(stream.state()).isEqualTo(Http2StreamState.HALF_CLOSED_LOCAL);
    }

    @Test
    void testTransitionOpenToHalfClosedRemote() {
        var stream = new Http2Stream(1, 65535);
        stream.transitionTo(Http2StreamState.OPEN);
        stream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);

        assertThat(stream.state()).isEqualTo(Http2StreamState.HALF_CLOSED_REMOTE);
    }

    @Test
    void testTransitionHalfClosedToClosed() {
        var stream = new Http2Stream(1, 65535);
        stream.transitionTo(Http2StreamState.OPEN);
        stream.transitionTo(Http2StreamState.HALF_CLOSED_LOCAL);
        stream.transitionTo(Http2StreamState.CLOSED);

        assertThat(stream.isClosed()).isTrue();
    }

    @Test
    void testInvalidTransition() {
        var stream = new Http2Stream(1, 65535);

        assertThatThrownBy(() -> stream.transitionTo(Http2StreamState.CLOSED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testCannotTransitionFromClosed() {
        var stream = new Http2Stream(1, 65535);
        stream.transitionTo(Http2StreamState.OPEN);
        stream.transitionTo(Http2StreamState.CLOSED);

        assertThatThrownBy(() -> stream.transitionTo(Http2StreamState.OPEN))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testConsumeSendWindow() {
        var stream = new Http2Stream(1, 65535);

        assertThat(stream.consumeSendWindow(1000)).isTrue();
        assertThat(stream.sendWindowSize()).isEqualTo(64535);
    }

    @Test
    void testConsumeSendWindowExceeded() {
        var stream = new Http2Stream(1, 100);

        assertThat(stream.consumeSendWindow(200)).isFalse();
        assertThat(stream.sendWindowSize()).isEqualTo(100);
    }

    @Test
    void testAdjustSendWindow() {
        var stream = new Http2Stream(1, 65535);
        stream.adjustSendWindow(1000);

        assertThat(stream.sendWindowSize()).isEqualTo(66535);
    }

    @Test
    void testAddAndGetData() {
        var stream = new Http2Stream(1, 65535);
        stream.addData(ByteBuffer.wrap("hello ".getBytes()));
        stream.addData(ByteBuffer.wrap("world".getBytes()));

        var combined = stream.getAccumulatedData();
        var bytes = new byte[combined.remaining()];
        combined.get(bytes);

        assertThat(new String(bytes)).isEqualTo("hello world");
    }

    @Test
    void testClearData() {
        var stream = new Http2Stream(1, 65535);
        stream.addData(ByteBuffer.wrap("data".getBytes()));
        stream.clearData();

        assertThat(stream.getAccumulatedData().remaining()).isEqualTo(0);
    }

    @Test
    void testClientInitiated() {
        assertThat(new Http2Stream(1, 65535).isClientInitiated()).isTrue();
        assertThat(new Http2Stream(3, 65535).isClientInitiated()).isTrue();
        assertThat(new Http2Stream(2, 65535).isClientInitiated()).isFalse();
    }

    @Test
    void testServerInitiated() {
        assertThat(new Http2Stream(2, 65535).isServerInitiated()).isTrue();
        assertThat(new Http2Stream(4, 65535).isServerInitiated()).isTrue();
        assertThat(new Http2Stream(1, 65535).isServerInitiated()).isFalse();
    }

    @Test
    void testIsOpen() {
        var stream = new Http2Stream(1, 65535);
        assertThat(stream.isOpen()).isFalse();

        stream.transitionTo(Http2StreamState.OPEN);
        assertThat(stream.isOpen()).isTrue();

        stream.transitionTo(Http2StreamState.HALF_CLOSED_LOCAL);
        assertThat(stream.isOpen()).isTrue();
    }

    @Test
    void testHeaders() {
        var stream = new Http2Stream(1, 65535);
        stream.headers().set(":method", "GET");
        stream.headers().set(":path", "/");

        assertThat(stream.headers().get(":method")).isEqualTo("GET");
        assertThat(stream.headers().get(":path")).isEqualTo("/");
    }
}
