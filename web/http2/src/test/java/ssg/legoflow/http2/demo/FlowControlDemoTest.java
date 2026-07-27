package ssg.legoflow.http2.demo;

import ssg.legoflow.http2.config.Http2Config;
import ssg.legoflow.http2.frame.Http2FrameType;
import ssg.legoflow.http2.stream.Http2FlowControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FlowControlDemoTest {

    @Test
    void testSendSmallPayload() {
        var demo = new FlowControlDemo();
        var payload = new byte[1000];
        var frames = demo.sendLargePayload(1, payload);

        assertThat(frames).isNotEmpty();
        assertThat(frames.getFirst().type()).isEqualTo(Http2FrameType.DATA);
    }

    @Test
    void testSendLargePayloadChunked() {
        var config = Http2Config.defaults().maxFrameSize(16384);
        var demo = new FlowControlDemo(config);

        var payload = new byte[50000];
        var frames = demo.sendLargePayload(1, payload);

        assertThat(frames.size()).isGreaterThan(1);
        int totalSent = frames.stream()
                .mapToInt(f -> f.payload().remaining())
                .sum();
        assertThat(totalSent).isEqualTo(50000);
    }

    @Test
    void testWindowExhaustion() {
        var config = Http2Config.defaults().initialWindowSize(100);
        var demo = new FlowControlDemo(config);

        var payload = new byte[500];
        var frames = demo.sendLargePayload(1, payload);

        int totalSent = frames.stream()
                .mapToInt(f -> f.payload().remaining())
                .sum();
        assertThat(totalSent).isLessThanOrEqualTo(100);
    }

    @Test
    void testWindowUpdateRestoresCapacity() {
        var config = Http2Config.defaults().initialWindowSize(100);
        var demo = new FlowControlDemo(config);

        demo.sendLargePayload(1, new byte[100]);
        assertThat(demo.getStreamSendWindow(1)).isEqualTo(0);

        demo.applyWindowUpdate(1, 50);
        assertThat(demo.getStreamSendWindow(1)).isEqualTo(50);
    }

    @Test
    void testConnectionWindowTracking() {
        var demo = new FlowControlDemo();
        int initialWindow = demo.getConnectionSendWindow();
        assertThat(initialWindow).isEqualTo(Http2FlowControl.DEFAULT_INITIAL_WINDOW_SIZE);

        demo.sendLargePayload(1, new byte[1000]);
        assertThat(demo.getConnectionSendWindow()).isEqualTo(initialWindow - 1000);
    }

    @Test
    void testConnectionWindowUpdate() {
        var demo = new FlowControlDemo();
        demo.sendLargePayload(1, new byte[1000]);

        int afterSend = demo.getConnectionSendWindow();
        demo.applyWindowUpdate(0, 500);

        assertThat(demo.getConnectionSendWindow()).isEqualTo(afterSend + 500);
    }

    @Test
    void testEndStreamOnLastFrame() {
        var demo = new FlowControlDemo();
        var payload = new byte[100];
        var frames = demo.sendLargePayload(1, payload);

        var lastFrame = frames.getLast();
        assertThat(lastFrame.hasFlag((byte) 0x1)).isTrue();
    }
}
