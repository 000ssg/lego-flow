package ssg.legoflow.upnp.mediarenderer;

/**
 * Enumeration of AVTransport transport states.
 *
 * <p>Represents the current playback state of a media renderer
 * as defined by the UPnP AVTransport:1 service specification.
 *
 * @since 0.1.0
 */
public enum TransportState {

    /** No media is loaded. */
    NO_MEDIA_PRESENT("NO_MEDIA_PRESENT"),

    /** Media is loaded but not playing. */
    STOPPED("STOPPED"),

    /** Media is actively playing. */
    PLAYING("PLAYING"),

    /** Playback is paused. */
    PAUSED_PLAYBACK("PAUSED_PLAYBACK"),

    /** The transport is transitioning between states. */
    TRANSITIONING("TRANSITIONING"),

    /** Recording is paused (not typically used in renderers). */
    PAUSED_RECORDING("PAUSED_RECORDING"),

    /** Actively recording (not typically used in renderers). */
    RECORDING("RECORDING");

    private final String value;

    TransportState(String value) {
        this.value = value;
    }

    /**
     * Returns the UPnP string value for this state.
     *
     * @return the state string
     * @since 0.1.0
     */
    public String value() {
        return value;
    }

    /**
     * Parses a transport state from its UPnP string value.
     *
     * @param value the state string
     * @return the transport state
     * @throws IllegalArgumentException if the value is unknown
     * @since 0.1.0
     */
    public static TransportState fromValue(String value) {
        for (TransportState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown transport state: " + value);
    }
}
