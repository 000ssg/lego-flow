package ssg.legoflow.media.rtsp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * State machine controlling media stream playback transitions.
 *
 * <p>Enforces valid state transitions per RFC 7826:
 * <pre>
 *   INIT --SETUP--> READY --PLAY--> PLAYING
 *                   READY <--PAUSE-- PLAYING
 *                   READY --PLAY--> PLAYING
 *                   READY --RECORD--> RECORDING
 *                   READY <--PAUSE-- RECORDING
 *                   any --TEARDOWN--> TEARDOWN
 * </pre>
 *
 * @since 0.1.0
 */
public final class StreamController {

    private static final Logger LOG = LoggerFactory.getLogger(StreamController.class);

    private volatile StreamState state;
    private volatile double position;

    /**
     * Creates a stream controller in INIT state.
     */
    public StreamController() {
        this.state = StreamState.INIT;
        this.position = 0.0;
    }

    /**
     * Returns the current stream state.
     *
     * @return the current state
     */
    public StreamState state() {
        return state;
    }

    /**
     * Returns the current playback position in seconds.
     *
     * @return the position
     */
    public double position() {
        return position;
    }

    /**
     * Transitions to READY state (after SETUP).
     *
     * @throws IllegalStateException if the transition is not valid
     */
    public synchronized void setup() {
        if (state != StreamState.INIT && state != StreamState.READY) {
            throw new IllegalStateException("Cannot SETUP in state " + state);
        }
        state = StreamState.READY;
        LOG.debug("Stream state: READY");
    }

    /**
     * Transitions to PLAYING state.
     *
     * @param startPosition the start position in seconds
     * @throws IllegalStateException if the transition is not valid
     */
    public synchronized void play(double startPosition) {
        if (state != StreamState.READY && state != StreamState.PAUSED) {
            throw new IllegalStateException("Cannot PLAY in state " + state);
        }
        this.position = startPosition;
        state = StreamState.PLAYING;
        LOG.debug("Stream state: PLAYING at {}", startPosition);
    }

    /**
     * Transitions to PAUSED state.
     *
     * @throws IllegalStateException if the transition is not valid
     */
    public synchronized void pause() {
        if (state != StreamState.PLAYING && state != StreamState.RECORDING) {
            throw new IllegalStateException("Cannot PAUSE in state " + state);
        }
        state = StreamState.PAUSED;
        LOG.debug("Stream state: PAUSED at {}", position);
    }

    /**
     * Transitions to RECORDING state.
     *
     * @throws IllegalStateException if the transition is not valid
     */
    public synchronized void record() {
        if (state != StreamState.READY) {
            throw new IllegalStateException("Cannot RECORD in state " + state);
        }
        state = StreamState.RECORDING;
        LOG.debug("Stream state: RECORDING");
    }

    /**
     * Transitions to TEARDOWN state from any state.
     */
    public synchronized void teardown() {
        state = StreamState.TEARDOWN;
        LOG.debug("Stream state: TEARDOWN");
    }

    /**
     * Updates the playback position.
     *
     * @param position the new position in seconds
     */
    public void updatePosition(double position) {
        this.position = position;
    }

    /**
     * Returns true if the stream is actively playing or recording.
     *
     * @return true if active
     */
    public boolean isActive() {
        return state == StreamState.PLAYING || state == StreamState.RECORDING;
    }

    @Override
    public String toString() {
        return "StreamController[state=" + state + ", position=" + position + "]";
    }
}
