package ssg.legoflow.upnp.dlna;

/**
 * Enumeration of DLNA device profiles.
 *
 * <p>Each profile represents a distinct DLNA device role as defined by
 * the DLNA (Digital Living Network Alliance) guidelines. Devices advertise
 * their profile so that control points can determine capabilities.
 *
 * @since 1.0.0
 */
public enum DlnaProfile {

    /**
     * Digital Media Server — stores and serves media content.
     *
     * @since 1.0.0
     */
    DIGITAL_MEDIA_SERVER("DMS"),

    /**
     * Digital Media Renderer — renders (plays) media content received from the network.
     *
     * @since 1.0.0
     */
    DIGITAL_MEDIA_RENDERER("DMR"),

    /**
     * Digital Media Player — discovers, selects, and plays media content locally.
     *
     * @since 1.0.0
     */
    DIGITAL_MEDIA_PLAYER("DMP"),

    /**
     * Digital Media Controller — discovers media on servers and directs renderers to play it.
     *
     * @since 1.0.0
     */
    DIGITAL_MEDIA_CONTROLLER("DMC");

    private final String code;

    DlnaProfile(String code) {
        this.code = code;
    }

    /**
     * Returns the short code for this profile (e.g. "DMS", "DMR").
     *
     * @return the DLNA profile code
     * @since 1.0.0
     */
    public String code() {
        return code;
    }

    /**
     * Looks up a profile by its short code.
     *
     * @param code the profile code (e.g. "DMS")
     * @return the matching profile
     * @throws IllegalArgumentException if the code is unknown
     * @since 1.0.0
     */
    public static DlnaProfile fromCode(String code) {
        for (DlnaProfile profile : values()) {
            if (profile.code.equals(code)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown DLNA profile code: " + code);
    }
}
