package ssg.legoflow.media.rtsp.server;

/**
 * Stream playback states for the RTSP session state machine.
 *
 * @since 1.0.0
 */
public enum StreamState {

    /** Initial state, no transport established. */
    INIT,

    /** Transport established via SETUP, ready to play. */
    READY,

    /** Media is actively being streamed. */
    PLAYING,

    /** Media playback is paused. */
    PAUSED,

    /** Media is being recorded. */
    RECORDING,

    /** Session has been torn down. */
    TEARDOWN
}
