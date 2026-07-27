package ssg.legoflow.http2.stream;

import ssg.legoflow.http2.frame.Http2Frame;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class Http2FlowControlTest {

    @Test
    void testDefaultInitialWindowSize() {
        var fc = new Http2FlowControl();

        assertThat(fc.connectionSendWindow()).isEqualTo(65535);
        assertThat(fc.connectionReceiveWindow()).isEqualTo(65535);
        assertThat(fc.initialWindowSize()).isEqualTo(65535);
    }

    @Test
    void testCustomInitialWindowSize() {
        var fc = new Http2FlowControl(1048576);

        assertThat(fc.connectionSendWindow()).isEqualTo(1048576);
        assertThat(fc.connectionReceiveWindow()).isEqualTo(1048576);
    }

    @Test
    void testConsumeConnectionSendWindow() {
        var fc = new Http2FlowControl();

        assertThat(fc.consumeConnectionSendWindow(1000)).isTrue();
        assertThat(fc.connectionSendWindow()).isEqualTo(64535);
    }

    @Test
    void testConsumeConnectionSendWindowExceeded() {
        var fc = new Http2FlowControl(100);

        assertThat(fc.consumeConnectionSendWindow(200)).isFalse();
        assertThat(fc.connectionSendWindow()).isEqualTo(100);
    }

    @Test
    void testApplyConnectionWindowUpdate() {
        var fc = new Http2FlowControl();
        fc.consumeConnectionSendWindow(1000);
        fc.applyConnectionWindowUpdate(500);

        assertThat(fc.connectionSendWindow()).isEqualTo(65035);
    }

    @Test
    void testApplyStreamWindowUpdate() {
        var fc = new Http2FlowControl();
        var stream = new Http2Stream(1, 65535);
        stream.consumeSendWindow(1000);

        fc.applyStreamWindowUpdate(stream, 500);

        assertThat(stream.sendWindowSize()).isEqualTo(65035);
    }

    @Test
    void testWindowUpdateZeroIncrement() {
        var fc = new Http2FlowControl();

        assertThatThrownBy(() -> fc.applyConnectionWindowUpdate(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testWindowUpdateNegativeIncrement() {
        var fc = new Http2FlowControl();

        assertThatThrownBy(() -> fc.applyConnectionWindowUpdate(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCreateWindowUpdate() {
        var fc = new Http2FlowControl();
        var frame = fc.createWindowUpdate(1, 32768);

        assertThat(frame.streamId()).isEqualTo(1);
        var payload = frame.payload();
        assertThat(payload.getInt() & 0x7FFFFFFF).isEqualTo(32768);
    }

    @Test
    void testProcessWindowUpdateConnection() {
        var fc = new Http2FlowControl();
        fc.consumeConnectionSendWindow(10000);

        var frame = Http2Frame.windowUpdate(0, 5000);
        fc.processWindowUpdate(frame, null);

        assertThat(fc.connectionSendWindow()).isEqualTo(60535);
    }

    @Test
    void testProcessWindowUpdateStream() {
        var fc = new Http2FlowControl();
        var stream = new Http2Stream(1, 65535);
        stream.consumeSendWindow(10000);

        var frame = Http2Frame.windowUpdate(1, 5000);
        fc.processWindowUpdate(frame, stream);

        assertThat(stream.sendWindowSize()).isEqualTo(60535);
    }

    @Test
    void testCalculateMaxSendSize() {
        var fc = new Http2FlowControl(100);
        var stream = new Http2Stream(1, 50);

        assertThat(fc.calculateMaxSendSize(stream, 200)).isEqualTo(50);
        assertThat(fc.calculateMaxSendSize(stream, 30)).isEqualTo(30);
    }

    @Test
    void testRestoreConnectionReceiveWindow() {
        var fc = new Http2FlowControl();
        fc.consumeConnectionReceiveWindow(1000);
        fc.restoreConnectionReceiveWindow(1000);

        assertThat(fc.connectionReceiveWindow()).isEqualTo(65535);
    }
}
