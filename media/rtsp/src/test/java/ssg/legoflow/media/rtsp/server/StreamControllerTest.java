package ssg.legoflow.media.rtsp.server;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link StreamController}.
 */
class StreamControllerTest {

    @Test
    void testInitialState() {
        var controller = new StreamController();
        assertThat(controller.state()).isEqualTo(StreamState.INIT);
        assertThat(controller.position()).isEqualTo(0.0);
        assertThat(controller.isActive()).isFalse();
    }

    @Test
    void testSetupTransition() {
        var controller = new StreamController();
        controller.setup();
        assertThat(controller.state()).isEqualTo(StreamState.READY);
    }

    @Test
    void testPlayTransition() {
        var controller = new StreamController();
        controller.setup();
        controller.play(0.0);
        assertThat(controller.state()).isEqualTo(StreamState.PLAYING);
        assertThat(controller.isActive()).isTrue();
    }

    @Test
    void testPlayWithPosition() {
        var controller = new StreamController();
        controller.setup();
        controller.play(30.5);
        assertThat(controller.position()).isEqualTo(30.5);
    }

    @Test
    void testPauseTransition() {
        var controller = new StreamController();
        controller.setup();
        controller.play(0.0);
        controller.pause();
        assertThat(controller.state()).isEqualTo(StreamState.PAUSED);
        assertThat(controller.isActive()).isFalse();
    }

    @Test
    void testResumeAfterPause() {
        var controller = new StreamController();
        controller.setup();
        controller.play(0.0);
        controller.pause();
        controller.play(10.0);
        assertThat(controller.state()).isEqualTo(StreamState.PLAYING);
        assertThat(controller.position()).isEqualTo(10.0);
    }

    @Test
    void testRecordTransition() {
        var controller = new StreamController();
        controller.setup();
        controller.record();
        assertThat(controller.state()).isEqualTo(StreamState.RECORDING);
        assertThat(controller.isActive()).isTrue();
    }

    @Test
    void testTeardownFromAnyState() {
        var controller = new StreamController();
        controller.teardown();
        assertThat(controller.state()).isEqualTo(StreamState.TEARDOWN);

        controller = new StreamController();
        controller.setup();
        controller.play(0);
        controller.teardown();
        assertThat(controller.state()).isEqualTo(StreamState.TEARDOWN);
    }

    @Test
    void testPlayFromInitThrows() {
        var controller = new StreamController();
        assertThatThrownBy(() -> controller.play(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INIT");
    }

    @Test
    void testPauseFromReadyThrows() {
        var controller = new StreamController();
        controller.setup();
        assertThatThrownBy(controller::pause)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY");
    }

    @Test
    void testRecordFromPlayingThrows() {
        var controller = new StreamController();
        controller.setup();
        controller.play(0);
        assertThatThrownBy(controller::record)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testSetupFromPlayingThrows() {
        var controller = new StreamController();
        controller.setup();
        controller.play(0);
        assertThatThrownBy(controller::setup)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testUpdatePosition() {
        var controller = new StreamController();
        controller.setup();
        controller.play(0);
        controller.updatePosition(45.0);
        assertThat(controller.position()).isEqualTo(45.0);
    }

    @Test
    void testPauseRecording() {
        var controller = new StreamController();
        controller.setup();
        controller.record();
        controller.pause();
        assertThat(controller.state()).isEqualTo(StreamState.PAUSED);
    }

    @Test
    void testSetupFromReady() {
        var controller = new StreamController();
        controller.setup();
        // Setup again from READY is valid (additional streams)
        controller.setup();
        assertThat(controller.state()).isEqualTo(StreamState.READY);
    }

    @Test
    void testToString() {
        var controller = new StreamController();
        assertThat(controller.toString()).contains("INIT");
    }
}
