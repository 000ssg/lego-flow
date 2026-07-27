package ssg.legoflow.upnp.mediarenderer;

/**
 * Transport information from the AVTransport service.
 *
 * @param currentTransportState  the current transport state
 * @param currentTransportStatus the current transport status
 * @param currentSpeed           the current playback speed (e.g. "1" for normal)
 * @since 1.0.0
 */
public record TransportInfo(
        TransportState currentTransportState,
        TransportStatus currentTransportStatus,
        String currentSpeed
) {

    /**
     * Creates transport info for a typical normal-speed state.
     *
     * @param state the transport state
     * @return the transport info
     * @since 1.0.0
     */
    public static TransportInfo of(TransportState state) {
        return new TransportInfo(state, TransportStatus.OK, "1");
    }
}
