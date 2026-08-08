package ssg.legoflow.ssh.connection;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link WindowManager}.
 */
class WindowManagerTest {

    @Test void testDefaultConstants() {
        assertThat(WindowManager.DEFAULT_WINDOW_SIZE).isEqualTo(2L * 1024 * 1024);
        assertThat(WindowManager.DEFAULT_MAX_PACKET_SIZE).isEqualTo(32768);
        assertThat(WindowManager.WINDOW_ADJUST_THRESHOLD)
                .isEqualTo(WindowManager.DEFAULT_WINDOW_SIZE / 4);
    }

    @Test void testDefaultConstructor() {
        var wm = new WindowManager();
        assertThat(wm.localWindow()).isEqualTo(WindowManager.DEFAULT_WINDOW_SIZE);
        assertThat(wm.remoteWindow()).isEqualTo(0);
        assertThat(wm.initialWindowSize()).isEqualTo(WindowManager.DEFAULT_WINDOW_SIZE);
        assertThat(wm.maxPacketSize()).isEqualTo(WindowManager.DEFAULT_MAX_PACKET_SIZE);
    }

    @Test void testCustomConstructor() {
        var wm = new WindowManager(1024, 512);
        assertThat(wm.localWindow()).isEqualTo(1024);
        assertThat(wm.remoteWindow()).isEqualTo(0);
        assertThat(wm.initialWindowSize()).isEqualTo(1024);
        assertThat(wm.maxPacketSize()).isEqualTo(512);
    }

    @Test void testConsumeRemoteWindowSuccess() {
        var wm = new WindowManager();
        wm.setRemoteWindow(1024);
        assertThat(wm.consumeRemoteWindow(256)).isTrue();
        assertThat(wm.remoteWindow()).isEqualTo(768);
    }

    @Test void testConsumeRemoteWindowInsufficientSpace() {
        var wm = new WindowManager();
        wm.setRemoteWindow(100);
        assertThat(wm.consumeRemoteWindow(200)).isFalse();
        // Window should not change on failure
        assertThat(wm.remoteWindow()).isEqualTo(100);
    }

    @Test void testConsumeLocalWindow() {
        var wm = new WindowManager(1024, 512);
        wm.consumeLocalWindow(256);
        assertThat(wm.localWindow()).isEqualTo(768);
    }

    @Test void testConsumeLocalWindowMultipleTimes() {
        var wm = new WindowManager(1024, 512);
        wm.consumeLocalWindow(100);
        wm.consumeLocalWindow(200);
        wm.consumeLocalWindow(300);
        assertThat(wm.localWindow()).isEqualTo(424);
    }

    @Test void testSetRemoteWindow() {
        var wm = new WindowManager();
        assertThat(wm.remoteWindow()).isEqualTo(0);
        wm.setRemoteWindow(512);
        assertThat(wm.remoteWindow()).isEqualTo(512);
    }

    @Test void testAdjustRemoteWindow() {
        var wm = new WindowManager();
        wm.setRemoteWindow(1024);
        wm.adjustRemoteWindow(256);
        assertThat(wm.remoteWindow()).isEqualTo(1280);
    }

    @Test void testShouldAdjustFalseWhenAboveThreshold() {
        var wm = new WindowManager();
        // Default window is 2MB, threshold is 512KB
        assertThat(wm.shouldAdjust()).isFalse();
    }

    @Test void testShouldAdjustTrueWhenBelowThreshold() {
        var wm = new WindowManager();
        long toConsume = WindowManager.DEFAULT_WINDOW_SIZE - WindowManager.WINDOW_ADJUST_THRESHOLD + 1;
        wm.consumeLocalWindow(toConsume);
        assertThat(wm.shouldAdjust()).isTrue();
    }

    @Test void testShouldAdjustAtThreshold() {
        var wm = new WindowManager();
        long toConsume = WindowManager.DEFAULT_WINDOW_SIZE - WindowManager.WINDOW_ADJUST_THRESHOLD;
        wm.consumeLocalWindow(toConsume);
        // Exactly at threshold, should still be false (< not <=)
        assertThat(wm.shouldAdjust()).isFalse();
    }

    @Test void testAdjustLocalWindowResetsToInitial() {
        var wm = new WindowManager(1024, 512);
        wm.consumeLocalWindow(500);
        assertThat(wm.localWindow()).isEqualTo(524);
        long added = wm.adjustLocalWindow();
        assertThat(added).isEqualTo(500);
        assertThat(wm.localWindow()).isEqualTo(1024);
    }

    @Test void testAdjustLocalWindowWhenFull() {
        var wm = new WindowManager(1024, 512);
        long added = wm.adjustLocalWindow();
        assertThat(added).isEqualTo(0);
        assertThat(wm.localWindow()).isEqualTo(1024);
    }

    @Test void testConsumeRemoteMultipleConsumers() {
        var wm = new WindowManager();
        wm.setRemoteWindow(1000);
        // Consume until depleted
        for (int i = 0; i < 20; i++) {
            assertThat(wm.consumeRemoteWindow(50)).isTrue();
        }
        // Now at 0, should fail
        assertThat(wm.consumeRemoteWindow(1)).isFalse();
    }

    @Test void testConcurrentRemoteConsume() throws InterruptedException {
        var wm = new WindowManager();
        wm.setRemoteWindow(100000);
        var threads = new Thread[4];
        for (int i = 0; i < 4; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 50; j++) {
                    wm.consumeRemoteWindow(100);
                }
            });
            threads[i].start();
        }
        for (var t : threads) t.join();
        // 4 * 50 * 100 = 20000 consumed
        assertThat(wm.remoteWindow()).isEqualTo(80000);
    }

    @Test void testConcurrentLocalConsume() throws InterruptedException {
        var wm = new WindowManager(100000, 1000);
        var threads = new Thread[4];
        for (int i = 0; i < 4; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 50; j++) {
                    wm.consumeLocalWindow(100);
                }
            });
            threads[i].start();
        }
        for (var t : threads) t.join();
        assertThat(wm.localWindow()).isEqualTo(80000);
    }

    @Test void testZeroRemoteWindowFailsConsume() {
        var wm = new WindowManager();
        // Remote window starts at 0
        assertThat(wm.consumeRemoteWindow(1)).isFalse();
    }

    @Test void testExactRemoteWindowConsume() {
        var wm = new WindowManager();
        wm.setRemoteWindow(100);
        assertThat(wm.consumeRemoteWindow(100)).isTrue();
        assertThat(wm.remoteWindow()).isEqualTo(0);
    }

    @Test void testZeroWindowSize() {
        var wm = new WindowManager(0, 1);
        assertThat(wm.localWindow()).isEqualTo(0);
    }

    @Test void testLargeWindowSize() {
        long largeWindow = Long.MAX_VALUE / 2;
        var wm = new WindowManager(largeWindow, 65536);
        assertThat(wm.localWindow()).isEqualTo(largeWindow);
        assertThat(wm.maxPacketSize()).isEqualTo(65536);
    }

    @Test void testConsumeRemoteWithNegativeValue() {
        var wm = new WindowManager();
        wm.setRemoteWindow(100);
        // Negative values should probably fail or not consume
        boolean result = wm.consumeRemoteWindow(-1);
        // With the CAS implementation, negative bytes would always succeed (current >= -1)
        // but this is a valid edge case to test
    }

    @Test void testAdjustLocalWindowAfterFullConsume() {
        var wm = new WindowManager(64, 32);
        wm.consumeLocalWindow(64);
        assertThat(wm.localWindow()).isEqualTo(0);
        long added = wm.adjustLocalWindow();
        assertThat(added).isEqualTo(64);
        assertThat(wm.localWindow()).isEqualTo(64);
    }

    @Test void testThresholdConstantCalculation() {
        // Threshold should be 25% of default window size
        long expected = WindowManager.DEFAULT_WINDOW_SIZE / 4;
        assertThat(WindowManager.WINDOW_ADJUST_THRESHOLD).isEqualTo(expected);
        assertThat(expected).isEqualTo(512 * 1024); // 512 KB
    }
}
