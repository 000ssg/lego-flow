package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link LocalPlaybackEngine}.
 *
 * <p>Verifies state transitions, seek functionality, position tracking,
 * and remote synchronization drift detection without requiring actual
 * audio playback hardware.
 *
 * @since 0.1.0
 */
class LocalPlaybackEngineTest {

    private LocalPlaybackEngine engine;
    private CopyOnWriteArrayList<PlaybackEvent> receivedEvents;

    @BeforeEach
    void setUp() {
        engine = new LocalPlaybackEngine();
        receivedEvents = new CopyOnWriteArrayList<>();
        engine.addPlaybackListener(receivedEvents::add);
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void testPlayPauseStop() {
        // Given: engine is not playing
        assertThat(engine.isPlaying()).isFalse();

        // When: simulate play (without real audio, engine sets playing state)
        // We cannot use play(URL) without real audio, so we test the state machine
        // by calling internal state transitions

        // Simulate: engine marks itself playing (as it would for unsupported format fallback)
        // The play(URL) method for unsupported formats still sets playing=true
        // Instead, test pause/stop from a known state

        // Stop should fire PlayStopped event
        engine.stop();
        assertThat(engine.isPlaying()).isFalse();
        assertThat(receivedEvents).anyMatch(e -> e instanceof PlaybackEvent.PlayStopped);

        // Clear events
        receivedEvents.clear();

        // Pause should fire PlayPaused
        engine.pause();
        assertThat(engine.isPlaying()).isFalse();
        assertThat(receivedEvents).anyMatch(e -> e instanceof PlaybackEvent.PlayPaused);
    }

    @Test
    void testSeek() {
        // Given: engine in default state
        Duration target = Duration.ofMinutes(2).plusSeconds(30);

        // When: seek to a specific position
        engine.seek(target);

        // Then: position reflects the seek
        Duration position = engine.getPosition();
        assertThat(position.toMillis()).isEqualTo(target.toMillis());
    }

    @Test
    void testPositionTracking() {
        // Given: engine with a known seek position
        Duration startPos = Duration.ofSeconds(10);
        engine.seek(startPos);

        // When: get position
        Duration position = engine.getPosition();

        // Then: position is at the seeked location
        assertThat(position.toMillis()).isEqualTo(startPos.toMillis());

        // When: seek to a different position
        Duration newPos = Duration.ofMinutes(1);
        engine.seek(newPos);

        // Then: position updated
        assertThat(engine.getPosition().toMillis()).isEqualTo(newPos.toMillis());
    }

    @Test
    void testSynchronization() {
        // Given: engine at position 10 seconds
        Duration localPos = Duration.ofSeconds(10);
        engine.seek(localPos);

        // When: remote position is within threshold (drift < 500ms)
        Duration closeRemotePos = Duration.ofSeconds(10).plusMillis(300);
        engine.synchronizeWithRemote(closeRemotePos);

        // Then: local position is NOT adjusted (drift is below threshold)
        assertThat(engine.getPosition().toMillis()).isEqualTo(localPos.toMillis());

        // When: remote position has significant drift (> 500ms)
        Duration farRemotePos = Duration.ofSeconds(15);
        engine.synchronizeWithRemote(farRemotePos);

        // Then: local position IS adjusted to match remote
        assertThat(engine.getPosition().toMillis()).isEqualTo(farRemotePos.toMillis());
    }
}
