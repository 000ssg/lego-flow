package ssg.legoflow.media.rtsp.protocol;

/**
 * RTSP 2.0 request methods as defined in RFC 7826.
 *
 * @since 0.1.0
 */
public enum RtspMethod {

    /** Query server or media capabilities. */
    OPTIONS,

    /** Retrieve media description (SDP). */
    DESCRIBE,

    /** Establish transport for a media stream. */
    SETUP,

    /** Start media playback. */
    PLAY,

    /** Pause media playback. */
    PAUSE,

    /** Terminate session and free resources. */
    TEARDOWN,

    /** Get session or media parameters (also used as keep-alive). */
    GET_PARAMETER,

    /** Set session or media parameters. */
    SET_PARAMETER,

    /** Announce media availability or update description. */
    ANNOUNCE,

    /** Start recording. */
    RECORD;

    /**
     * Parses a method name (case-insensitive).
     *
     * @param name the method name
     * @return the matching method
     * @throws IllegalArgumentException if the name is unknown
     */
    public static RtspMethod fromName(String name) {
        for (RtspMethod m : values()) {
            if (m.name().equalsIgnoreCase(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown RTSP method: " + name);
    }
}
